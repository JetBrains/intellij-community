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
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.keyFMap.KeyFMap;
import com.intellij.util.text.CharSequenceHashingStrategy;
import gnu.trove.THashSet;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;

/**
 * @author max
 */
public class VirtualDirectoryImpl extends VirtualFileSystemEntry {
  private static final Logger LOG = Logger.getInstance(VirtualDirectoryImpl.class);

  private static final boolean CHECK = ApplicationManager.getApplication().isUnitTestMode();

  private static final VirtualDirectoryImpl NULL_VIRTUAL_FILE =
    new VirtualDirectoryImpl(-42, new VfsData.Segment(null), new VfsData.DirectoryData(), null, LocalFileSystem.getInstance()) {
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
    boolean caseSensitive = delegate.isCaseSensitive();
    VirtualFileSystemEntry result = doFindChild(name, ensureCanonicalName, delegate, caseSensitive);

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

  @Nullable // null if there can't be a child with this name, NULL_VIRTUAL_FILE
  private VirtualFileSystemEntry doFindChildInArray(@NotNull String name, boolean caseSensitive) {
    if (myData.isAdoptedName(name)) return NULL_VIRTUAL_FILE;
    int[] array = myData.myChildrenIds;
    int indexInReal = findIndex(array, name, caseSensitive);
    if (indexInReal >= 0) {
      return mySegment.vfsData.getFileById(array[indexInReal], this);
    }
    return null;
  }

  @Nullable // null if there can't be a child with this name, NULL_VIRTUAL_FILE if cached as absent, the file if found
  private VirtualFileSystemEntry doFindChild(@NotNull String name,
                                             boolean ensureCanonicalName,
                                             @NotNull NewVirtualFileSystem delegate,
                                             boolean caseSensitive) {
    if (name.isEmpty()) {
      return null;
    }
    if (!isValid()) {
      throw new InvalidVirtualFileAccessException(this);
    }

    VirtualFileSystemEntry found = doFindChildInArray(name, caseSensitive);
    if (found != null) return found;

    if (ensureCanonicalName) {
      String trimmedName = deSlash(name);
      if (trimmedName == null) return null;
      if (!trimmedName.equals(name)) {
        found = doFindChildInArray(trimmedName, caseSensitive);
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
      child = doFindChildInArray(name, caseSensitive);
      if (child != null) return child; // including NULL_VIRTUAL_FILE
      if (allChildrenLoaded()) {
        return null;
      }

      // do not extract getId outside the synchronized block since it will cause a concurrency problem.
      int id = ourPersistence.getId(this, name, delegate);
      if (id <= 0) {
        myData.addAdoptedName(name, caseSensitive);
        return null;
      }
      child = createChild(FileNameCache.storeName(name), id, delegate);

      addChild(child);

      ((PersistentFSImpl)ourPersistence).incStructuralModificationCount();
    }

    if (!child.isDirectory()) {
      // access check should only be called when child is actually added to the parent, otherwise it may break VirtualFilePointers validity
      //noinspection TestOnlyProblems
      VfsRootAccess.assertAccessInTests(child, getFileSystem());
    }

    return child;
  }

  // removes forward/back slashes from start/end and return trimmed name or null if there are slashes in the middle or it's empty
  private static String deSlash(@NotNull String name) {
    int startTrimmed = -1;
    int endTrimmed = -1;
    for (int i=0;i<name.length();i++) {
      char c = name.charAt(i);
      if (startTrimmed == -1) {
        if (!isFileSeparator(c)) {
          startTrimmed = i;
        }
      }
      else if (endTrimmed == -1) {
        if (isFileSeparator(c)) {
          endTrimmed = i;
        }
      }
      else if (!isFileSeparator(c)) {
        return null; // there are slashes in the middle
      }
    }
    if (startTrimmed == -1) return null;
    if (endTrimmed == -1) return name.substring(startTrimmed);
    if (startTrimmed == endTrimmed) return null;
    return name.substring(startTrimmed, endTrimmed);
  }

  private static boolean isFileSeparator(char c) {
    return c == '/' || c=='\\';
  }

  @NotNull
  private VirtualFileSystemEntry[] getArraySafely() {
    if (myId < 0) throw new InvalidVirtualFileAccessException(this);
    return myData.getFileChildren(this);
  }

  @NotNull
  public VirtualFileSystemEntry createChild(@NotNull String name, int id, @NotNull NewVirtualFileSystem delegate) {
    synchronized (myData) {
      return createChild(FileNameCache.storeName(name), id, delegate);
    }
  }

  @NotNull
  private VirtualFileSystemEntry createChild(int nameId, int id, @NotNull NewVirtualFileSystem delegate) {
    FileLoadingTracker.fileLoaded(this, nameId);

    final int attributes = ourPersistence.getFileAttributes(id);
    VfsData.Segment segment = mySegment.vfsData.getSegment(id, true);
    try {
      VfsData.initFile(id, segment, nameId,
                       PersistentFS.isDirectory(attributes) ? new VfsData.DirectoryData() : KeyFMap.EMPTY_MAP);
    }
    catch (VfsData.FileAlreadyCreatedException e) {
      throw new RuntimeException("dir=" + myId + "; dir.children=" + Arrays.toString(FSRecords.listAll(myId)), e);
    }
    LOG.assertTrue(!(getFileSystem() instanceof Win32LocalFileSystem));

    VirtualFileSystemEntry child = mySegment.vfsData.getFileById(id, this);
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
    final boolean caseSensitive = getFileSystem().isCaseSensitive();
    VirtualFileSystemEntry found = doFindChildInArray(name, caseSensitive);
    //noinspection UseVirtualFileEquals
    return found == NULL_VIRTUAL_FILE ? null : found;
  }

  @Override
  @NotNull
  public Iterable<VirtualFile> iterInDbChildren() {
    if (!ourPersistence.wereChildrenAccessed(this)) {
      return Collections.emptyList();
    }

    if (ourPersistence.areChildrenLoaded(this)) {
      return Arrays.asList(getChildren()); // may load vfs from other projects
    }

    loadPersistedChildren();

    return getCachedChildren();
  }

  @NotNull
  @Override
  public Iterable<VirtualFile> iterInDbChildrenWithoutLoadingVfsFromOtherProjects() {
    if (!ourPersistence.wereChildrenAccessed(this)) {
      return Collections.emptyList();
    }

    if (!ourPersistence.areChildrenLoaded(this)) {
      loadPersistedChildren();
    }
    return getCachedChildren();
  }

  private void loadPersistedChildren() {
    final String[] names = ourPersistence.listPersisted(this);
    final NewVirtualFileSystem delegate = PersistentFS.replaceWithNativeFS(getFileSystem());
    for (String name : names) {
      findChild(name, false, false, delegate);
    }
  }

  @Override
  @NotNull
  public VirtualFile[] getChildren() {
    if (!isValid()) {
      throw new InvalidVirtualFileAccessException(this);
    }
    if (allChildrenLoaded()) {
      return getArraySafely();
    }

    return loadAllChildren();
  }

  @NotNull
  private VirtualFile[] loadAllChildren() {
    NewVirtualFileSystem delegate = getFileSystem();
    boolean caseSensitive = delegate.isCaseSensitive();
    synchronized (myData) {
      final boolean wasChildrenLoaded = ourPersistence.areChildrenLoaded(this);
      final FSRecords.NameId[] childrenIds = ourPersistence.listAll(this);
      int[] result = ArrayUtil.newIntArray(childrenIds.length);
      VirtualFile[] files = childrenIds.length == 0 ? VirtualFile.EMPTY_ARRAY : new VirtualFile[childrenIds.length];
      if (childrenIds.length != 0) {
        Arrays.sort(childrenIds, (o1, o2) -> {
          CharSequence name1 = o1.name;
          CharSequence name2 = o2.name;
          int cmp = compareNames(name1, name2, caseSensitive);
          if (cmp == 0 && name1 != name2) {
            LOG.error(ourPersistence + " returned duplicate file names(" + name1 + "," + name2 + ")" +
                      " caseSensitive: " + caseSensitive +
                      " SystemInfo.isFileSystemCaseSensitive: " + SystemInfo.isFileSystemCaseSensitive +
                      " SystemInfo.OS: " + SystemInfo.OS_NAME + " " + SystemInfo.OS_VERSION +
                      " wasChildrenLoaded: " + wasChildrenLoaded +
                      " in the dir: " + this + ";" +
                      " children: " + Arrays.toString(childrenIds));
          }
          return cmp;
        });
        TIntHashSet prevChildren = new TIntHashSet(myData.myChildrenIds);
        for (int i = 0; i < childrenIds.length; i++) {
          FSRecords.NameId child = childrenIds[i];
          result[i] = child.id;
          assert child.id > 0 : child;
          prevChildren.remove(child.id);
          VirtualFileSystemEntry file = mySegment.vfsData.getFileById(child.id, this);
          if (file == null) {
            file = createChild(child.nameId, child.id, delegate);
          }
          files[i] = file;
        }
        if (!prevChildren.isEmpty()) {
          LOG.error("Loaded child disappeared: " +
                    "parent=" + verboseToString(this) +
                    "; child=" + verboseToString(mySegment.vfsData.getFileById(prevChildren.toArray()[0], this)));
        }
      }

      if (getId() > 0) {
        myData.clearAdoptedNames();
        myData.myChildrenIds = result;
        setChildrenLoaded();
        if (CHECK) {
          assertConsistency(caseSensitive, Arrays.asList(childrenIds));
        }
      }

      return files;
    }
  }

  private void assertConsistency(boolean caseSensitive, @NotNull Object details) {
    if (!CHECK || ApplicationInfoImpl.isInStressTest()) return;
    int[] childrenIds = myData.myChildrenIds;
    if (childrenIds.length == 0) return;
    CharSequence prevName = mySegment.vfsData.getNameByFileId(childrenIds[0]);
    for (int i = 1; i < childrenIds.length; i++) {
      int id = childrenIds[i];
      int prev = childrenIds[i - 1];
      CharSequence name = mySegment.vfsData.getNameByFileId(id);
      int cmp = compareNames(name, prevName, caseSensitive);
      prevName = name;
      if (cmp <= 0) {
        error(verboseToString(mySegment.vfsData.getFileById(prev, this)) +
              " is wrongly placed before " +
              verboseToString(mySegment.vfsData.getFileById(id, this)), getArraySafely(), details);
      }
      synchronized (myData) {
        if (myData.isAdoptedName(name)) {
          try {
            error("In " + verboseToString(this) + " file '" + name + "' is both child and adopted",
                  getArraySafely(), "Adopted: " + myData.getAdoptedNames() + ";\n " + details);
          }
          finally {
            myData.removeAdoptedName(name);
          }
        }
      }
    }
  }

  @NotNull
  private static String verboseToString(VirtualFileSystemEntry file) {
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
  }

  private static void error(String message, VirtualFileSystemEntry[] array, Object... details) {
    String children = StringUtil.join(array, VirtualDirectoryImpl::verboseToString, "\n");
    String detailsStr = StringUtil.join(ContainerUtil.<Object, Object>map(details, o -> o instanceof Object[] ? Arrays.toString((Object[])o) : o), "\n");
    throw new AssertionError(message + "; children: " + children + "\nDetails: " + detailsStr);
  }

  @Override
  @Nullable
  public VirtualFileSystemEntry findChild(@NotNull final String name) {
    return findChild(name, false, true, getFileSystem());
  }

  @SuppressWarnings("deprecation")
  @Override
  public VirtualFileSystemEntry findChildById(int id) {
    int i = ArrayUtil.indexOf(myData.myChildrenIds, id);
    if (i >= 0) {
      return mySegment.vfsData.getFileById(id, this);
    }

    String name = ourPersistence.getName(id);
    return findChild(name, false, false, getFileSystem());
  }

  @NotNull
  @Override
  public byte[] contentsToByteArray() throws IOException {
    throw new IOException("Cannot get content of directory: " + this);
  }

  // optimisation: works faster than added.forEach(this::addChild)
  public void createAndAddChildren(@NotNull List<? extends FSRecords.NameId> added) {
    if (added.size()<=1) {
      for (FSRecords.NameId pair : added) {
        VirtualFileSystemEntry file = createChild(pair.name.toString(), pair.id, getFileSystem());
        addChild(file);
      }
      return;
    }

    // merge sorted added and existing lists just like in merge sort
    final boolean caseSensitive = getFileSystem().isCaseSensitive();
    Comparator<FSRecords.NameId> pairComparator = (p1, p2) -> compareNames(p1.name, p2.name, caseSensitive);
    added.sort(pairComparator);
    TIntArrayList mergedIds = new TIntArrayList(myData.myChildrenIds.length + added.size());
    synchronized (myData) {
      for (FSRecords.NameId pair : added) {
        myData.removeAdoptedName(pair.name);
        createChild(pair.name.toString(), pair.id, getFileSystem());
      }
      ContainerUtil.processSortedListsInOrder(added, new AbstractList<FSRecords.NameId>() {
        @Override
        public FSRecords.NameId get(int index) {
          int id = myData.myChildrenIds[index];
          CharSequence name = ObjectUtils.assertNotNull(mySegment.vfsData.getNameByFileId(id));
          return new FSRecords.NameId(id, -1, name);
        }

        @Override
        public int size() {
          return myData.myChildrenIds.length;
        }
      }, pairComparator, true, pair -> mergedIds.add(pair.id));
      myData.myChildrenIds = mergedIds.toNativeArray();

      assertConsistency(caseSensitive, added);
    }
  }

  public void addChild(@NotNull VirtualFileSystemEntry child) {
    final CharSequence childName = child.getNameSequence();
    final boolean caseSensitive = getFileSystem().isCaseSensitive();
    synchronized (myData) {
      myData.removeAdoptedName(childName);
      int indexInReal = findIndex(myData.myChildrenIds, childName, caseSensitive);
      if (indexInReal < 0) {
        insertChildAt(child, indexInReal);
      }
      // else already stored
      assertConsistency(caseSensitive, child);
    }
  }

  private void insertChildAt(@NotNull VirtualFileSystemEntry file, int negativeIndex) {
    int i = -negativeIndex -1;
    int id = file.getId();
    assert id > 0 : file;
    myData.myChildrenIds = ArrayUtil.insert(myData.myChildrenIds, i, id);
  }

  public void removeChild(@NotNull VirtualFile file) {
    boolean caseSensitive = getFileSystem().isCaseSensitive();
    String name = file.getName();
    synchronized (myData) {
      int indexInReal = findIndex(myData.myChildrenIds, name, caseSensitive);
      if (indexInReal >= 0) {
        // there suddenly can be that we ask to add name to adopted whereas it already contained in the real part
        // in this case we should remove it from there
        myData.myChildrenIds = ArrayUtil.remove(myData.myChildrenIds, indexInReal);
      }
      if (!allChildrenLoaded()) {
        myData.addAdoptedName(name, caseSensitive);
      }

      assertConsistency(caseSensitive, file);
    }
  }

  // optimization: faster than forEach(this::removeChild)
  public void removeChildren(@NotNull TIntHashSet idsToRemove, @NotNull List<? extends CharSequence> namesToRemove) {
    boolean caseSensitive = getFileSystem().isCaseSensitive();
    synchronized (myData) {
      // remove from array by merging two sorted lists
      int[] newIds = new int[myData.myChildrenIds.length];
      int[] oldIds = myData.myChildrenIds;
      int o = 0;
      for (int oldId : oldIds) {
        if (!idsToRemove.contains(oldId)) {
          newIds[o++] = oldId;
        }
      }
      if (o != newIds.length) {
        newIds = o == 0 ? ArrayUtil.EMPTY_INT_ARRAY : Arrays.copyOf(newIds, o);
      }
      myData.myChildrenIds = newIds;

      if (!allChildrenLoaded()) {
        myData.addAdoptedNames(namesToRemove, caseSensitive);
      }

      assertConsistency(caseSensitive, namesToRemove);
    }
  }

  // check if all these names are not existing, remove invalid events from the list
  public void validateChildrenToCreate(@NotNull List<? extends VFileCreateEvent> childrenToCreate) {
    if (childrenToCreate.size() <= 1) {
      for (int i = childrenToCreate.size() - 1; i >= 0; i--) {
        VFileCreateEvent event = childrenToCreate.get(i);
        if (!event.isValid()) {
          childrenToCreate.remove(i);
        }
      }
      return;
    }
    boolean caseSensitive = getFileSystem().isCaseSensitive();

    Set<CharSequence> existingNames = new THashSet<>(myData.myChildrenIds.length, caseSensitive ? CharSequenceHashingStrategy.CASE_SENSITIVE : CharSequenceHashingStrategy.CASE_INSENSITIVE);
    for (int id : myData.myChildrenIds) {
      existingNames.add(mySegment.vfsData.getNameByFileId(id));
    }
    int id = getId();
    synchronized (myData) {
      if (id >= 0) {
        FSRecords.NameId[] persistentIds = FSRecords.listAll(id);
        for (FSRecords.NameId nameId : persistentIds) {
          existingNames.add(nameId.name);
        }
      }

      validateAgainst(childrenToCreate, existingNames);

      if (!childrenToCreate.isEmpty() && !allChildrenLoaded()) {
        // findChild asks delegate FS when failed to locate child, and so should we
        int beforeSize = existingNames.size();
        String[] names = getFileSystem().list(this);
        existingNames.addAll(Arrays.asList(names));
        if (beforeSize != existingNames.size()) {
          validateAgainst(childrenToCreate, existingNames);
        }
      }
    }
  }

  private void validateAgainst(@NotNull List<? extends VFileCreateEvent> childrenToCreate, @NotNull Set<CharSequence> existingNames) {
    for (int i = childrenToCreate.size() - 1; i >= 0; i--) {
      VFileCreateEvent event = childrenToCreate.get(i);
      String childName = event.getChildName();
      // assume there is no need to canonicalize names in VFileCreateEvent
      boolean childExists = !myData.isAdoptedName(childName) && existingNames.contains(childName);
      if (childExists) {
        childrenToCreate.remove(i);
      }
    }
  }

  public boolean allChildrenLoaded() {
    return getFlagInt(CHILDREN_CACHED);
  }
  private void setChildrenLoaded() {
    setFlagInt(CHILDREN_CACHED, true);
  }

  @NotNull
  public List<String> getSuspiciousNames() {
    return myData.getAdoptedNames();
  }

  private int findIndex(@NotNull int[] ids, @NotNull CharSequence name, boolean caseSensitive) {
    return ObjectUtils.binarySearch(0, ids.length, mid -> compareNames(mySegment.vfsData.getNameByFileId(ids[mid]), name, caseSensitive));
  }

  private static int compareNames(@NotNull CharSequence name1, @NotNull CharSequence name2, boolean caseSensitive) {
    int d = name1.length() - name2.length();
    if (d != 0) return d;
    for (int i = 0; i < name1.length(); i++) {
      // com.intellij.openapi.util.text.StringUtil.compare(String,String,boolean) inconsistent
      d = StringUtil.compare(name1.charAt(i), name2.charAt(i), !caseSensitive);
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