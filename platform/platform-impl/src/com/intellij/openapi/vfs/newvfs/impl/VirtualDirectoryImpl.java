/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vfs.newvfs.impl;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;

/**
 * @author max
 */
public class VirtualDirectoryImpl extends VirtualFileSystemEntry {
  public static boolean CHECK = ApplicationManager.getApplication().isUnitTestMode();

  static final VirtualDirectoryImpl NULL_VIRTUAL_FILE = new VirtualDirectoryImpl("*?;%NULL", null, LocalFileSystem.getInstance(), -42, 0) {
    public String toString() {
      return "NULL";
    }
  };

  private final NewVirtualFileSystem myFS;

  /**
   *   The array is logically divided into the two parts:
   *  - left subarray for storing real child files
   *  - right subarray for storing "adopted children" files.
   *  "Adopted children" are fake files which are used for storing names which were accessed via findFileByName() or similar calls.
   *  We have to store these unsuccessful find attempts to be able to correctly refresh in the future.
   *  See usages of {@link #getSuspiciousNames()} in the {@link com.intellij.openapi.vfs.newvfs.persistent.RefreshWorker}
   *
   *  Guarded by this, files in each subarray are sorted according to the compareNameTo() comparator
   *  TODO: revise the whole adopted scheme
   */
  private VirtualFileSystemEntry[] myChildren = EMPTY_ARRAY;

  public VirtualDirectoryImpl(@NonNls @NotNull final String name,
                              @Nullable final VirtualDirectoryImpl parent,
                              @NotNull final NewVirtualFileSystem fs,
                              final int id,
                              @PersistentFS.Attributes final int attributes) {
    super(name, parent, id, attributes);
    myFS = fs;
  }

  @Override
  @NotNull
  public NewVirtualFileSystem getFileSystem() {
    return myFS;
  }

  @Nullable
  private VirtualFileSystemEntry findChild(@NotNull String name,
                                           final boolean doRefresh,
                                           boolean ensureCanonicalName,
                                           @NotNull NewVirtualFileSystem delegate) {
    boolean ignoreCase = !delegate.isCaseSensitive();
    Comparator comparator = getComparator(name, ignoreCase);
    VirtualFileSystemEntry result = doFindChild(name, ensureCanonicalName, delegate, comparator);
    if (result == NULL_VIRTUAL_FILE) {
      result = doRefresh ? createAndFindChildWithEventFire(name, delegate) : null;
    }
    else if (result != null) {
      if (doRefresh && delegate.isDirectory(result) != result.isDirectory()) {
        RefreshQueue.getInstance().refresh(false, false, null, result);
        result = findChild(name, false, ensureCanonicalName, delegate);
      }
    }

    if (result == null) {
      addToAdoptedChildren(name, !delegate.isCaseSensitive(), comparator);
    }
    return result;
  }

  private synchronized void addToAdoptedChildren(@NotNull final String name,
                                                 final boolean ignoreCase,
                                                 @NotNull Comparator comparator) {
    long r = findIndexInBoth(myChildren, comparator);
    int indexInReal = (int)(r >> 32);
    int indexInAdopted = (int)r;
    if (indexInAdopted >= 0) return; //already added
    if (!allChildrenLoaded()) {
      insertChildAt(new AdoptedChild(name), indexInAdopted);
    }

    if (indexInReal >= 0) {
      // there suddenly can be that we ask to add name to adopted whereas it already contains in the real part
      // in this case we should remove it from there
      removeFromArray(indexInReal);
    }
    assertConsistency(myChildren, ignoreCase, name);
  }

  private static class AdoptedChild extends VirtualFileImpl {
    private AdoptedChild(String name) {
      super(name, NULL_VIRTUAL_FILE, -42, -1);
    }
  }

  @Nullable // null if there can't be a child with this name, NULL_VIRTUAL_FILE
  private synchronized VirtualFileSystemEntry doFindChildInArray(@NotNull Comparator comparator) {
    VirtualFileSystemEntry[] array = myChildren;
    long r = findIndexInBoth(array, comparator);
    int indexInReal = (int)(r >> 32);
    int indexInAdopted = (int)r;
    if (indexInAdopted >= 0) return NULL_VIRTUAL_FILE;

    if (indexInReal >= 0) {
      return array[indexInReal];
    }
    return null;
  }

  @Nullable // null if there can't be a child with this name, NULL_VIRTUAL_FILE if cached as absent, the file if found
  private VirtualFileSystemEntry doFindChild(@NotNull String name,
                                             boolean ensureCanonicalName,
                                             @NotNull NewVirtualFileSystem delegate,
                                             @NotNull Comparator comparator) {
    if (name.isEmpty()) {
      return null;
    }

    VirtualFileSystemEntry found = doFindChildInArray(comparator);
    if (found != null) return found;

    if (allChildrenLoaded()) {
      return NULL_VIRTUAL_FILE;
    }

    if (ensureCanonicalName) {
      VirtualFile fake = new FakeVirtualFile(this, name);
      name = delegate.getCanonicallyCasedName(fake);
      if (name.isEmpty()) return null;
    }

    synchronized (this) {
      // do not extract getId outside the synchronized block since it will cause a concurrency problem.
      int id = ourPersistence.getId(this, name, delegate);
      if (id <= 0) {
        return null;
      }
      // maybe another doFindChild() sneaked in the middle
      VirtualFileSystemEntry[] array = myChildren;
      long r = findIndexInBoth(array, comparator);
      int indexInReal = (int)(r >> 32);
      int indexInAdopted = (int)r;
      if (indexInAdopted >= 0) return NULL_VIRTUAL_FILE;
      // double check
      if (indexInReal >= 0) {
        return array[indexInReal];
      }

      String shorty = new String(name);
      VirtualFileSystemEntry child = createChild(shorty, id, delegate); // So we don't hold whole char[] buffer of a lengthy path

      VirtualFileSystemEntry[] after = myChildren;
      if (after != array)  {
        // in tests when we call assertAccessInTests it can load a huge number of files which lead to children modification
        // so fall back to slow path
        addChild(child);
      }
      else {
        insertChildAt(child, indexInReal);
        assertConsistency(myChildren, !delegate.isCaseSensitive(), name);
      }
      return child;
    }
  }

  @NotNull
  private static Comparator getComparator(@NotNull final String name, final boolean ignoreCase) {
    return new Comparator() {
      @Override
      public int compareMyKeyTo(@NotNull VirtualFileSystemEntry file) {
        return -file.compareNameTo(name, ignoreCase);
      }
    };
  }

  private synchronized VirtualFileSystemEntry[] getArraySafely() {
    return myChildren;
  }

  @NotNull
  public VirtualFileSystemEntry createChild(@NotNull String name, int id, @NotNull NewVirtualFileSystem delegate) {
    VirtualFileSystemEntry child;

    final int attributes = ourPersistence.getFileAttributes(id);
    if (PersistentFS.isDirectory(attributes)) {
      child = new VirtualDirectoryImpl(name, this, delegate, id, attributes);
    }
    else {
      child = new VirtualFileImpl(name, this, id, attributes);
      //noinspection TestOnlyProblems
      assertAccessInTests(child, delegate);
    }

    if (delegate.markNewFilesAsDirty()) {
      child.markDirty();
    }

    return child;
  }


  private static final boolean IS_UNDER_TEAMCITY = System.getProperty("bootstrap.testcases") != null;
  private static final boolean SHOULD_PERFORM_ACCESS_CHECK = System.getenv("NO_FS_ROOTS_ACCESS_CHECK") == null;

  private static final Collection<String> ourAdditionalRoots = new THashSet<String>();

  @TestOnly
  public static void allowRootAccess(@NotNull String... roots) {
    for (String root : roots) {
      ourAdditionalRoots.add(FileUtil.toSystemIndependentName(root));
    }
  }

  @TestOnly
  public static void disallowRootAccess(@NotNull String... roots) {
    for (String root : roots) {
      ourAdditionalRoots.remove(FileUtil.toSystemIndependentName(root));
    }
  }

  @TestOnly
  private static void assertAccessInTests(@NotNull VirtualFileSystemEntry child, @NotNull NewVirtualFileSystem delegate) {
    final Application application = ApplicationManager.getApplication();
    if (IS_UNDER_TEAMCITY &&
        SHOULD_PERFORM_ACCESS_CHECK &&
        application.isUnitTestMode() &&
        application instanceof ApplicationImpl &&
        ((ApplicationImpl)application).isComponentsCreated()) {
      if (delegate != LocalFileSystem.getInstance() && delegate != JarFileSystem.getInstance()) {
        return;
      }
      // root' children are loaded always
      if (child.getParent() == null || child.getParent().getParent() == null) return;

      Set<String> allowed = allowedRoots();
      boolean isUnder = allowed == null;
      if (!isUnder) {
        String childPath = child.getPath();
        if (delegate == JarFileSystem.getInstance()) {
          VirtualFile local = JarFileSystem.getInstance().getVirtualFileForJar(child);
          assert local != null : child;
          childPath = local.getPath();
        }
        for (String root : allowed) {
          if (FileUtil.startsWith(childPath, root)) {
            isUnder = true;
            break;
          }
          if (root.startsWith(JarFileSystem.PROTOCOL_PREFIX)) {
            String rootLocalPath = FileUtil.toSystemIndependentName(PathUtil.toPresentableUrl(root));
            isUnder = FileUtil.startsWith(childPath, rootLocalPath);
            if (isUnder) break;
          }
        }
      }

      assert isUnder || allowed.isEmpty() : "File accessed outside allowed roots: " + child + ";\nAllowed roots: " + new ArrayList<String>(allowed);
    }
  }

  // null means we were unable to get roots, so do not check access
  @Nullable
  @TestOnly
  private static Set<String> allowedRoots() {
    if (insideGettingRoots) return null;

    Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    if (openProjects.length == 0) return null;

    final Set<String> allowed = new THashSet<String>();
    allowed.add(FileUtil.toSystemIndependentName(PathManager.getHomePath()));

    try {
      URL outUrl = Application.class.getResource("/");
      String output = new File(outUrl.toURI()).getParentFile().getParentFile().getPath();
      allowed.add(FileUtil.toSystemIndependentName(output));
    }
    catch (URISyntaxException ignored) { }

    allowed.add(FileUtil.toSystemIndependentName(SystemProperties.getJavaHome()));
    allowed.add(FileUtil.toSystemIndependentName(new File(FileUtil.getTempDirectory()).getParent()));
    allowed.add(FileUtil.toSystemIndependentName(System.getProperty("java.io.tmpdir")));
    allowed.add(FileUtil.toSystemIndependentName(SystemProperties.getUserHome()));

    for (Project project : openProjects) {
      if (!project.isInitialized()) {
        return null; // all is allowed
      }
      for (VirtualFile root : ProjectRootManager.getInstance(project).getContentRoots()) {
        allowed.add(root.getPath());
      }
      for (VirtualFile root : getAllRoots(project)) {
        allowed.add(StringUtil.trimEnd(root.getPath(), JarFileSystem.JAR_SEPARATOR));
      }
      String location = project.getBasePath();
      assert location != null : project;
      allowed.add(FileUtil.toSystemIndependentName(location));
    }

    allowed.addAll(ourAdditionalRoots);

    return allowed;
  }

  private static boolean insideGettingRoots;

  @TestOnly
  private static VirtualFile[] getAllRoots(@NotNull Project project) {
    insideGettingRoots = true;
    final Set<VirtualFile> roots = new THashSet<VirtualFile>();

    final OrderEnumerator enumerator = ProjectRootManager.getInstance(project).orderEntries();
    ContainerUtil.addAll(roots, enumerator.getClassesRoots());
    ContainerUtil.addAll(roots, enumerator.getSourceRoots());

    insideGettingRoots = false;
    return VfsUtilCore.toVirtualFileArray(roots);
  }

  @Nullable
  private VirtualFileSystemEntry createAndFindChildWithEventFire(@NotNull String name, @NotNull NewVirtualFileSystem delegate) {
    final VirtualFile fake = new FakeVirtualFile(this, name);
    final FileAttributes attributes = delegate.getAttributes(fake);
    if (attributes == null) return null;
    final String realName = delegate.getCanonicallyCasedName(fake);
    final VFileCreateEvent event = new VFileCreateEvent(null, this, realName, attributes.isDirectory(), true);
    RefreshQueue.getInstance().processSingleEvent(event);
    return findChild(realName);
  }

  @Override
  @Nullable
  public NewVirtualFile refreshAndFindChild(@NotNull String name) {
    return findChild(name, true, true, getFileSystem());
  }

  private static int findIndexInOneHalf(final VirtualFileSystemEntry[] array,
                                        int start,
                                        int end,
                                        final boolean isAdopted,
                                        @NotNull final Comparator comparator) {
    return binSearch(array, start, end, new Comparator() {
      @Override
      public int compareMyKeyTo(@NotNull VirtualFileSystemEntry file) {
        if (isAdopted && !isAdoptedChild(file)) return 1;
        if (!isAdopted && isAdoptedChild(file)) return -1;
        return comparator.compareMyKeyTo(file);
      }
    });
  }

  // returns two int indices packed into one long. left index is for the real file array half, right is for the adopted children name array
  private static long findIndexInBoth(@NotNull VirtualFileSystemEntry[] array, @NotNull Comparator comparator) {
    int high = array.length - 1;
    if (high == -1) {
      return pack(-1, -1);
    }
    int low = 0;
    boolean startInAdopted = isAdoptedChild(array[low]);
    boolean endInAdopted = isAdoptedChild(array[high]);
    if (startInAdopted == endInAdopted) {
      int index = findIndexInOneHalf(array, low, high + 1, startInAdopted, comparator);
      int otherIndex = startInAdopted ? -1 : -array.length - 1;
      return startInAdopted ? pack(otherIndex, index) : pack(index, otherIndex);
    }
    boolean adopted = false;
    int cmp = -1;
    int mid = -1;
    int foundIndex = -1;
    while (low <= high) {
      mid = low + high >>> 1;
      VirtualFileSystemEntry file = array[mid];
      cmp = comparator.compareMyKeyTo(file);
      adopted = isAdoptedChild(file);
      if (cmp == 0) {
        foundIndex = mid;
        break;
      }
      if ((adopted || cmp <= 0) && (!adopted || cmp >= 0)) {
        int indexInAdopted = findIndexInOneHalf(array, mid + 1, high + 1, true, comparator);
        int indexInReal = findIndexInOneHalf(array, low, mid, false, comparator);
        return pack(indexInReal, indexInAdopted);
      }

      if (cmp > 0) {
        low = mid + 1;
      }
      else {
        high = mid - 1;
      }
    }

    // key not found.
    if (cmp != 0) foundIndex = -low-1;
    int newStart = adopted ? low : mid + 1;
    int newEnd = adopted ? mid + 1 : high + 1;
    int theOtherHalfIndex = newStart < newEnd ? findIndexInOneHalf(array, newStart, newEnd, !adopted, comparator) : -newStart-1;
    return adopted ? pack(theOtherHalfIndex, foundIndex) : pack(foundIndex, theOtherHalfIndex);
  }

  private static long pack(int indexInReal, int indexInAdopted) {
    return (long)indexInReal << 32 | (indexInAdopted & 0xffffffffL);
  }

  @Override
  @Nullable
  public synchronized NewVirtualFile findChildIfCached(@NotNull String name) {
    final boolean ignoreCase = !getFileSystem().isCaseSensitive();
    Comparator comparator = getComparator(name, ignoreCase);
    VirtualFileSystemEntry found = doFindChildInArray(comparator);
    return found == NULL_VIRTUAL_FILE ? null : found;
  }

  @Override
  @NotNull
  public Iterable<VirtualFile> iterInDbChildren() {
    if (!ourPersistence.wereChildrenAccessed(this)) {
      return Collections.emptyList();
    }

    if (!ourPersistence.areChildrenLoaded(this)) {
      final String[] names = ourPersistence.listPersisted(this);
      final NewVirtualFileSystem delegate = PersistentFS.replaceWithNativeFS(getFileSystem());
      for (String name : names) {
        findChild(name, false, false, delegate);
      }
    }
    return getCachedChildren();
  }

  @Override
  @NotNull
  public synchronized VirtualFile[] getChildren() {
    VirtualFileSystemEntry[] children = myChildren;
    NewVirtualFileSystem delegate = getFileSystem();
    final boolean ignoreCase = !delegate.isCaseSensitive();
    if (allChildrenLoaded()) {
      assertConsistency(children, ignoreCase);
      return children;
    }

    FSRecords.NameId[] childrenIds = ourPersistence.listAll(this);
    VirtualFileSystemEntry[] result;
    if (childrenIds.length == 0) {
      result = EMPTY_ARRAY;
    }
    else {
      Arrays.sort(childrenIds, new java.util.Comparator<FSRecords.NameId>() {
        @Override
        public int compare(FSRecords.NameId o1, FSRecords.NameId o2) {
          String name1 = o1.name;
          String name2 = o2.name;
          return compareNames(name1, name2, ignoreCase);
        }
      });
      result = new VirtualFileSystemEntry[childrenIds.length];
      int delegateI = 0;
      int i = 0;

      int cachedEnd = getAdoptedChildrenStart();
      // merge (sorted) children[0..cachedEnd) and childrenIds into the result array.
      // file that is already in children array must be copied into the result as is
      // for the file name that is new in childrenIds the file must be created and copied into result
      while (delegateI < childrenIds.length) {
        FSRecords.NameId nameId = childrenIds[delegateI];
        while (i < cachedEnd && children[i].compareNameTo(nameId.name, ignoreCase) < 0) i++; // skip files that are not in childrenIds

        VirtualFileSystemEntry resultFile;
        if (i < cachedEnd && children[i].compareNameTo(nameId.name, ignoreCase) == 0) {
          resultFile = children[i++];
        }
        else {
          resultFile = createChild(nameId.name, nameId.id, delegate);
        }
        result[delegateI++] = resultFile;
      }

      assertConsistency(result, ignoreCase, children, cachedEnd, childrenIds);
    }

    if (getId() > 0) {
      myChildren = result;
      setChildrenLoaded();
    }

    return result;
  }

  private void assertConsistency(@NotNull VirtualFileSystemEntry[] array, boolean ignoreCase, @NotNull Object... details) {
    if (!CHECK) return;
    boolean allChildrenLoaded = allChildrenLoaded();
    for (int i = 0; i < array.length; i++) {
      VirtualFileSystemEntry file = array[i];
      boolean isAdopted = isAdoptedChild(file);
      assert !isAdopted || !allChildrenLoaded;
      if (isAdopted && i != array.length - 1) {
        assert isAdoptedChild(array[i + 1]);
      }
      if (i != 0) {
        VirtualFileSystemEntry prev = array[i - 1];
        String prevName = prev.getName();
        int cmp = file.compareNameTo(prevName, ignoreCase);
        if (cmp == 0) {
          Function<VirtualFileSystemEntry, String> verboseToString = new Function<VirtualFileSystemEntry, String>() {
            @Override
            public String fun(VirtualFileSystemEntry entry) {
              return entry + " (name: '" + entry.getName()
                     + "', " + entry.getClass()
                     + ", parent:"+entry.getParent()
                     + "; id:"+entry.getId()
                     + "; FS:" +entry.getFileSystem()
                     + "; delegate.attrs:" +entry.getFileSystem().getAttributes(entry)
                     + "; caseSensitive:" +entry.getFileSystem().isCaseSensitive()
                     + "; canonical:" +entry.getFileSystem().getCanonicallyCasedName(entry)
                     + ") ";
            }
          };
          String children = StringUtil.join(array, verboseToString, ",");
          throw new AssertionError(
            verboseToString.fun(prev) + " equals to " + verboseToString.fun(file) + "; children: " + children + "\nDetails: " + ContainerUtil.map(
              details, new Function<Object, Object>() {
              @Override
              public Object fun(Object o) {
                return o instanceof Object[] ? Arrays.toString((Object[])o) : o;
              }
            }));
        }

        if (isAdopted == isAdoptedChild(prev)) {
          assert cmp > 0 : "Not sorted. "+Arrays.toString(details);
        }
      }
    }
  }

  @Override
  @Nullable
  public VirtualFileSystemEntry findChild(@NotNull final String name) {
    return findChild(name, false, true, getFileSystem());
  }

  public VirtualFileSystemEntry findChildById(int id, boolean cachedOnly) {
    VirtualFile[] array = getArraySafely();
    VirtualFileSystemEntry result = null;
    for (VirtualFile file : array) {
      VirtualFileSystemEntry withId = (VirtualFileSystemEntry)file;
      if (withId.getId() == id) {
        result = withId;
        break;
      }
    }
    if (result != null) return result;
    if (cachedOnly) return null;

    String name = ourPersistence.getName(id);
    return findChild(name, false, false, getFileSystem());
  }

  @NotNull
  @Override
  public byte[] contentsToByteArray() throws IOException {
    throw new IOException("Cannot get content of directory: " + this);
  }

  public synchronized void addChild(@NotNull VirtualFileSystemEntry child) {
    VirtualFileSystemEntry[] array = myChildren;
    final String childName = child.getName();
    final boolean ignoreCase = !getFileSystem().isCaseSensitive();
    long r = findIndexInBoth(array, getComparator(childName, ignoreCase));
    int indexInReal = (int)(r >> 32);
    int indexInAdopted = (int)r;

    if (indexInAdopted >= 0) {
      // remove Adopted first
      removeFromArray(indexInAdopted);
    }
    if (indexInReal < 0) {
      insertChildAt(child, indexInReal);
    }
    // else already stored
    assertConsistency(myChildren, ignoreCase, child);
  }

  private void insertChildAt(@NotNull VirtualFileSystemEntry file, int negativeIndex) {
    @NotNull VirtualFileSystemEntry[] array = myChildren;
    VirtualFileSystemEntry[] appended = new VirtualFileSystemEntry[array.length + 1];
    int i = -negativeIndex -1;
    System.arraycopy(array, 0, appended, 0, i);
    appended[i] = file;
    System.arraycopy(array, i, appended, i + 1, array.length - i);
    myChildren = appended;
  }

  public synchronized void removeChild(@NotNull VirtualFile file) {
    boolean ignoreCase = !getFileSystem().isCaseSensitive();
    String name = file.getName();

    addToAdoptedChildren(name, ignoreCase, getComparator(name, ignoreCase));
    assertConsistency(myChildren, ignoreCase, file);
  }

  private void removeFromArray(int index) {
    myChildren = ArrayUtil.remove(myChildren, index, new ArrayFactory<VirtualFileSystemEntry>() {
      @NotNull
      @Override
      public VirtualFileSystemEntry[] create(int count) {
        return new VirtualFileSystemEntry[count];
      }
    });
  }

  public boolean allChildrenLoaded() {
    return getFlagInt(CHILDREN_CACHED);
  }
  private void setChildrenLoaded() {
    setFlagInt(CHILDREN_CACHED, true);
  }

  @NotNull
  public synchronized List<String> getSuspiciousNames() {
    List<VirtualFile> suspicious = new SubList<VirtualFile>(myChildren, getAdoptedChildrenStart(), myChildren.length);
    return ContainerUtil.map2List(suspicious, new Function<VirtualFile, String>() {
      @Override
      public String fun(VirtualFile file) {
        return file.getName();
      }
    });
  }

  private int getAdoptedChildrenStart() {
    int index = binSearch(myChildren, 0, myChildren.length, new Comparator() {
      @Override
      public int compareMyKeyTo(@NotNull VirtualFileSystemEntry v) {
        return isAdoptedChild(v) ? -1 : 1;
      }
    });
    return -index - 1;
  }

  private static boolean isAdoptedChild(@NotNull VirtualFileSystemEntry v) {
    return v.getParent() == NULL_VIRTUAL_FILE;
  }

  private interface Comparator {
    int compareMyKeyTo(@NotNull VirtualFileSystemEntry file);
  }

  private static int binSearch(@NotNull VirtualFileSystemEntry[] array,
                               int start,
                               int end,
                               @NotNull Comparator comparator) {
    int low = start;
    int high = end - 1;
    assert low >= 0 && low <= array.length;

    while (low <= high) {
      int mid = low + high >>> 1;
      int cmp = comparator.compareMyKeyTo(array[mid]);
      if (cmp > 0) {
        low = mid + 1;
      }
      else if (cmp < 0) {
        high = mid - 1;
      }
      else {
        return mid; // key found
      }
    }
    return -(low + 1);  // key not found.
  }

  @Override
  public boolean isDirectory() {
    return true;
  }

  @Override
  @NotNull
  public synchronized List<VirtualFile> getCachedChildren() {
    return new SubList<VirtualFile>(myChildren, 0, getAdoptedChildrenStart());
  }

  @Override
  public InputStream getInputStream() throws IOException {
    throw new IOException("getInputStream() must not be called against a directory: " + getUrl());
  }

  @Override
  @NotNull
  public OutputStream getOutputStream(final Object requestor, final long newModificationStamp, final long newTimeStamp) throws IOException {
    throw new IOException("getOutputStream() must not be called against a directory: " + getUrl());
  }

  @Override
  public void markDirtyRecursively() {
    markDirty();
    markDirtyRecursivelyInternal();
  }

  // optimisation: do not travel up unnecessary
  private void markDirtyRecursivelyInternal() {
    for (VirtualFileSystemEntry child : getArraySafely()) {
      if (isAdoptedChild(child)) break;
      child.markDirtyInternal();
      if (child instanceof VirtualDirectoryImpl) {
        ((VirtualDirectoryImpl)child).markDirtyRecursivelyInternal();
      }
    }
  }
}
