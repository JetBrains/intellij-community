/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.InvalidVirtualFileAccessException;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.win32.Win32LocalFileSystem;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl;
import com.intellij.psi.impl.PsiCachedValue;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.UriUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.keyFMap.KeyFMap;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author max
 */
public class VirtualDirectoryImpl extends VirtualFileSystemEntry {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.newvfs.impl.VirtualDirectoryImpl");

  private static final boolean CHECK = ApplicationManager.getApplication().isUnitTestMode();

  private static final VirtualDirectoryImpl NULL_VIRTUAL_FILE =
    new VirtualDirectoryImpl(-42, new VfsData.Segment(), new VfsData.DirectoryData(), null, LocalFileSystem.getInstance()) {
      @Override
      public String toString() {
        return "NULL";
      }
    };

  private final VfsData.DirectoryData myData;
  private final NewVirtualFileSystem myFs;

  public VirtualDirectoryImpl(int id,
                              @NotNull VfsData.Segment segment,
                              @NotNull VfsData.DirectoryData data,
                              @Nullable VirtualDirectoryImpl parent,
                              @NotNull NewVirtualFileSystem fs) {
    super(id, segment, parent);
    myData = data;
    myFs = fs;
  }

  @Override
  @NotNull
  public NewVirtualFileSystem getFileSystem() {
    return myFs;
  }

  @Nullable
  private VirtualFileSystemEntry findChild(@NotNull String name,
                                           boolean doRefresh,
                                           boolean ensureCanonicalName,
                                           @NotNull NewVirtualFileSystem delegate) {
    boolean ignoreCase = !delegate.isCaseSensitive();
    VirtualFileSystemEntry result = doFindChild(name, ensureCanonicalName, delegate, ignoreCase);

    //noinspection UseVirtualFileEquals
    if (result == NULL_VIRTUAL_FILE) {
      result = doRefresh ? createAndFindChildWithEventFire(name, delegate) : null;
    }
    else if (result != null && doRefresh && delegate.isDirectory(result) != result.isDirectory()) {
      RefreshQueue.getInstance().refresh(false, false, null, result);
      result = findChild(name, false, ensureCanonicalName, delegate);
    }

    return result;
  }

  private void addToAdoptedChildren(final boolean ignoreCase, @NotNull final String name) {
    if (myData.isAdoptedName(name)) return; //already added
    if (!allChildrenLoaded()) {
      myData.addAdoptedName(name, getFileSystem().isCaseSensitive());
    }

    int indexInReal = findIndex(myData.myChildrenIds, name, ignoreCase);
    if (indexInReal >= 0) {
      // there suddenly can be that we ask to add name to adopted whereas it already contains in the real part
      // in this case we should remove it from there
      removeFromArray(indexInReal);
    }
    assertConsistency(ignoreCase, name);
  }

  @Nullable // null if there can't be a child with this name, NULL_VIRTUAL_FILE
  private VirtualFileSystemEntry doFindChildInArray(@NotNull String name, boolean ignoreCase) {
    synchronized (myData) {
      if (myData.isAdoptedName(name)) return NULL_VIRTUAL_FILE;

      int[] array = myData.myChildrenIds;
      int indexInReal = findIndex(array, name, ignoreCase);
      if (indexInReal >= 0) {
        return VfsData.getFileById(array[indexInReal], this);
      }
      return null;
    }
  }

  @Nullable // null if there can't be a child with this name, NULL_VIRTUAL_FILE if cached as absent, the file if found
  private VirtualFileSystemEntry doFindChild(@NotNull String name,
                                             boolean ensureCanonicalName,
                                             @NotNull NewVirtualFileSystem delegate,
                                             boolean ignoreCase) {
    if (name.isEmpty()) {
      return null;
    }
    if (!isValid()) {
      throw new InvalidVirtualFileAccessException(this);
    }

    VirtualFileSystemEntry found = doFindChildInArray(name, ignoreCase);
    if (found != null) return found;

    if (ensureCanonicalName) {
      String trimmedName = UriUtil.trimTrailingSlashes(UriUtil.trimLeadingSlashes(FileUtilRt.toSystemIndependentName(name)));
      if (trimmedName.indexOf('/') != -1) return null; // name must not contain slashes in the middle
      if (trimmedName.isEmpty()) return null;
      if (!trimmedName.equals(name)) {
        found = doFindChildInArray(trimmedName, ignoreCase);
        if (found != null) return found;
        name = trimmedName;
      }
    }

    if (allChildrenLoaded()) {
      return NULL_VIRTUAL_FILE;
    }

    if (ensureCanonicalName) {
      VirtualFile fake = new FakeVirtualFile(this, name);
      name = delegate.getCanonicallyCasedName(fake);
      if (name.isEmpty()) return null;
    }

    VirtualFileSystemEntry child;
    synchronized (myData) {
      // maybe another doFindChild() sneaked in the middle
      if (myData.isAdoptedName(name)) return NULL_VIRTUAL_FILE;

      int[] array = myData.myChildrenIds;
      int indexInReal = findIndex(array, name, ignoreCase);
      // double check
      if (indexInReal >= 0) {
        return VfsData.getFileById(array[indexInReal], this);
      }
      if (allChildrenLoaded()) {
        return null;
      }

      // do not extract getId outside the synchronized block since it will cause a concurrency problem.
      int id = ourPersistence.getId(this, name, delegate);
      if (id <= 0) {
        myData.addAdoptedName(name, !ignoreCase);
        return null;
      }
      child = createChild(FileNameCache.getInstance().storeName(name), id, delegate);

      int[] after = myData.myChildrenIds;
      if (after != array)  {
        // in tests when we call assertAccessInTests it can load a huge number of files which lead to children modification
        // so fall back to slow path
        addChild(child);
      }
      else {
        insertChildAt(child, indexInReal);
        assertConsistency(!delegate.isCaseSensitive(), name);
      }
    }

    if (!child.isDirectory()) {
      // access check should only be called when child is actually added to the parent, otherwise it may break VirtualFilePointers validity
      //noinspection TestOnlyProblems
      VfsRootAccess.assertAccessInTests(child, getFileSystem());
    }

    return child;
  }

  @NotNull
  private VirtualFileSystemEntry[] getArraySafely() {
    synchronized (myData) {
      return myData.getFileChildren(myId, this);
    }
  }

  @NotNull
  public VirtualFileSystemEntry createChild(String name, int id, @NotNull NewVirtualFileSystem delegate) {
    synchronized (myData) {
      return createChild(FileNameCache.getInstance().storeName(name), id, delegate);
    }
  }

  @NotNull
  private VirtualFileSystemEntry createChild(int nameId, int id, @NotNull NewVirtualFileSystem delegate) {
    final int attributes = ourPersistence.getFileAttributes(id);
    VfsData.Segment segment = VfsData.getSegment(id, true);
    try {
      VfsData.initFile(id, segment, nameId,
                       PersistentFS.isDirectory(attributes) ? new VfsData.DirectoryData() : KeyFMap.EMPTY_MAP);
    }
    catch (VfsData.FileAlreadyCreatedException e) {
      throw new RuntimeException("dir=" + myId, e);
    }
    LOG.assertTrue(!(getFileSystem() instanceof Win32LocalFileSystem));

    VirtualFileSystemEntry child = VfsData.getFileById(id, this);
    assert child != null;
    segment.setFlag(id, IS_SYMLINK_FLAG, PersistentFS.isSymLink(attributes));
    segment.setFlag(id, IS_SPECIAL_FLAG, PersistentFS.isSpecialFile(attributes));
    segment.setFlag(id, IS_WRITABLE_FLAG, PersistentFS.isWritable(attributes));
    segment.setFlag(id, IS_HIDDEN_FLAG, PersistentFS.isHidden(attributes));
    child.updateLinkStatus();

    if (delegate.markNewFilesAsDirty()) {
      child.markDirty();
    }

    return child;
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

  @Override
  @Nullable
  public NewVirtualFile findChildIfCached(@NotNull String name) {
    final boolean ignoreCase = !getFileSystem().isCaseSensitive();
    VirtualFileSystemEntry found = doFindChildInArray(name, ignoreCase);
    //noinspection UseVirtualFileEquals
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
  public VirtualFile[] getChildren() {
    if (!isValid()) {
      throw new InvalidVirtualFileAccessException(this);
    }
    NewVirtualFileSystem delegate = getFileSystem();
    final boolean ignoreCase = !delegate.isCaseSensitive();
    synchronized (myData) {
      if (allChildrenLoaded()) {
        assertConsistency(ignoreCase, "");
        return getArraySafely();
      }

      final boolean wasChildrenLoaded = ourPersistence.areChildrenLoaded(this);
      final FSRecords.NameId[] childrenIds = ourPersistence.listAll(this);
      int[] result;
      if (childrenIds.length == 0) {
        result = ArrayUtil.EMPTY_INT_ARRAY;
      }
      else {
        Arrays.sort(childrenIds, (o1, o2) -> {
          CharSequence name1 = o1.name;
          CharSequence name2 = o2.name;
          int cmp = compareNames(name1, name2, ignoreCase);
          if (cmp == 0 && name1 != name2) {
            LOG.error(ourPersistence + " returned duplicate file names(" + name1 + "," + name2 + ")" +
                      " ignoreCase: " + ignoreCase +
                      " SystemInfo.isFileSystemCaseSensitive: " + SystemInfo.isFileSystemCaseSensitive +
                      " SystemInfo.OS: " + SystemInfo.OS_NAME + " " + SystemInfo.OS_VERSION +
                      " wasChildrenLoaded: " + wasChildrenLoaded +
                      " in the dir: " + this + ";" +
                      " children: " + Arrays.toString(childrenIds));
          }
          return cmp;
        });
        TIntHashSet prevChildren = new TIntHashSet(myData.myChildrenIds);
        result = new int[childrenIds.length];
        for (int i = 0; i < childrenIds.length; i++) {
          FSRecords.NameId child = childrenIds[i];
          result[i] = child.id;
          assert child.id > 0 : child;
          prevChildren.remove(child.id);
          if (VfsData.getFileById(child.id, this) == null) {
            createChild(child.nameId, child.id, delegate);
          }
        }
        if (!prevChildren.isEmpty()) {
          LOG.error("Loaded child disappeared: " +
                    "parent=" + verboseToString.fun(this) +
                    "; child=" + verboseToString.fun(VfsData.getFileById(prevChildren.toArray()[0], this)));
        }
      }

      if (getId() > 0) {
        myData.myChildrenIds = result;
        if (CHECK) {
          assertConsistency(ignoreCase, Arrays.asList(childrenIds));
        }
        setChildrenLoaded();
      }

      return getArraySafely();
    }
  }

  private void assertConsistency(boolean ignoreCase, @NotNull Object details) {
    if (!CHECK || ApplicationInfoImpl.isInStressTest()) return;
    int[] childrenIds = myData.myChildrenIds;
    for (int i = 1; i < childrenIds.length; i++) {
      int id = childrenIds[i];
      int prev = childrenIds[i - 1];
      CharSequence name = VfsData.getNameByFileId(id);
      CharSequence prevName = VfsData.getNameByFileId(prev);
      int cmp = compareNames(name, prevName, ignoreCase);
      if (cmp <= 0) {
        error(verboseToString.fun(VfsData.getFileById(prev, this)) +
              " is wrongly placed before " +
              verboseToString.fun(VfsData.getFileById(id, this)), getArraySafely(), details);
      }
    }
  }

  private static final Function<VirtualFileSystemEntry, String> verboseToString = file -> {
    if (file == null) return "null";
    return file + " (name: '" + file.getName()
           + "', " + file.getClass()
           + ", parent: "+file.getParent()
           + "; id: "+file.getId()
           + "; FS: " +file.getFileSystem()
           + "; delegate.attrs: " +file.getFileSystem().getAttributes(file)
           + "; caseSensitive: " +file.getFileSystem().isCaseSensitive()
           + "; canonical: " +file.getFileSystem().getCanonicallyCasedName(file)
           + ") ";
  };

  private static void error(String message, VirtualFileSystemEntry[] array, Object... details) {
    String children = StringUtil.join(array, verboseToString, ",");
    throw new AssertionError(
      message + "; children: " + children + "\nDetails: " + ContainerUtil.map(
        details, o -> o instanceof Object[] ? Arrays.toString((Object[])o) : o));
  }

  @Override
  @Nullable
  public VirtualFileSystemEntry findChild(@NotNull final String name) {
    return findChild(name, false, true, getFileSystem());
  }

  public VirtualFileSystemEntry findChildById(int id, boolean cachedOnly) {
    if (ArrayUtil.indexOf(myData.myChildrenIds, id) >= 0) {
      return VfsData.getFileById(id, this);
    }
    if (cachedOnly) return null;

    String name = ourPersistence.getName(id);
    return findChild(name, false, false, getFileSystem());
  }

  @NotNull
  @Override
  public byte[] contentsToByteArray() throws IOException {
    throw new IOException("Cannot get content of directory: " + this);
  }

  public void addChild(@NotNull VirtualFileSystemEntry child) {
    final String childName = child.getName();
    final boolean ignoreCase = !getFileSystem().isCaseSensitive();
    synchronized (myData) {
      int indexInReal = findIndex(myData.myChildrenIds, childName, ignoreCase);

      myData.removeAdoptedName(childName);
      if (indexInReal < 0) {
        insertChildAt(child, indexInReal);
      }
      // else already stored
      assertConsistency(ignoreCase, child);
    }
  }

  private void insertChildAt(@NotNull VirtualFileSystemEntry file, int negativeIndex) {
    @NotNull int[] array = myData.myChildrenIds;
    int[] appended = new int[array.length + 1];
    int i = -negativeIndex -1;
    System.arraycopy(array, 0, appended, 0, i);
    appended[i] = file.getId();
    assert appended[i] > 0 : file;
    System.arraycopy(array, i, appended, i + 1, array.length - i);
    myData.myChildrenIds = appended;
    ((PersistentFSImpl)PersistentFS.getInstance()).incStructuralModificationCount();
  }

  public void removeChild(@NotNull VirtualFile file) {
    boolean ignoreCase = !getFileSystem().isCaseSensitive();
    String name = file.getName();
    synchronized (myData) {
      addToAdoptedChildren(ignoreCase, name);
      assertConsistency(ignoreCase, file);
    }
  }

  private void removeFromArray(int index) {
    myData.myChildrenIds = ArrayUtil.remove(myData.myChildrenIds, index);
    ((PersistentFSImpl)PersistentFS.getInstance()).incStructuralModificationCount();
  }

  public boolean allChildrenLoaded() {
    return getFlagInt(CHILDREN_CACHED);
  }
  private void setChildrenLoaded() {
    setFlagInt(CHILDREN_CACHED, true);
    myData.clearAdoptedNames();
  }

  @NotNull
  public List<String> getSuspiciousNames() {
    synchronized (myData) {
      return myData.getAdoptedNames();
    }
  }

  @SuppressWarnings("Duplicates")
  private static int findIndex(final int[] array, @NotNull CharSequence name, boolean ignoreCase) {
    int low = 0;
    int high = array.length - 1;

    while (low <= high) {
      int mid = low + high >>> 1;
      int cmp = -compareNames(VfsData.getNameByFileId(array[mid]), name, ignoreCase);
      if (cmp > 0) low = mid + 1;
      else if (cmp < 0) high = mid - 1;
      else return mid;
    }

    return -(low + 1);
  }

  private static int compareNames(@NotNull CharSequence name1, @NotNull CharSequence name2, boolean ignoreCase) {
    int d = name1.length() - name2.length();
    if (d != 0) return d;
    for (int i = 0; i < name1.length(); i++) {
      // com.intellij.openapi.util.text.StringUtil.compare(String,String,boolean) inconsistent
      d = StringUtil.compare(name1.charAt(i), name2.charAt(i), ignoreCase);
      if (d != 0) return d;
    }
    return 0;
  }

  @Override
  public boolean isDirectory() {
    return true;
  }

  @Override
  @NotNull
  public List<VirtualFile> getCachedChildren() {
    return Arrays.asList(getArraySafely());
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
      child.markDirtyInternal();
      if (child instanceof VirtualDirectoryImpl) {
        ((VirtualDirectoryImpl)child).markDirtyRecursivelyInternal();
      }
    }
  }

  @Override
  protected void setUserMap(@NotNull KeyFMap map) {
    myData.myUserMap = map;
  }

  @NotNull
  @Override
  protected KeyFMap getUserMap() {
    return myData.myUserMap;
  }

  @Override
  protected boolean changeUserMap(KeyFMap oldMap, KeyFMap newMap) {
    checkLeaks(newMap);
    return myData.changeUserMap(oldMap, UserDataInterner.internUserData(newMap));
  }

  static void checkLeaks(KeyFMap newMap) {
    for (Key<?> key : newMap.getKeys()) {
      if (key != null && newMap.get(key) instanceof PsiCachedValue) {
        throw new AssertionError("Don't store CachedValue in VFS user data, since it leads to memory leaks");
      }
    }
  }
}