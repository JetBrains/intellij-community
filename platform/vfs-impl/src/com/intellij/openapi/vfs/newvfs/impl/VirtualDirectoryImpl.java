// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.InvalidVirtualFileAccessException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.win32.Win32LocalFileSystem;
import com.intellij.openapi.vfs.newvfs.*;
import com.intellij.openapi.vfs.newvfs.events.ChildInfo;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl;
import com.intellij.psi.impl.PsiCachedValue;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PairConsumer;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.keyFMap.KeyFMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;

public class VirtualDirectoryImpl extends VirtualFileSystemEntry {
  private static final Logger LOG = Logger.getInstance(VirtualDirectoryImpl.class);

  private static final boolean CHECK = ApplicationManager.getApplication().isUnitTestMode();

  final VfsData.DirectoryData myData;
  private final NewVirtualFileSystem myFs;

  VirtualDirectoryImpl(int id,
                       @NotNull VfsData.Segment segment,
                       @NotNull VfsData.DirectoryData data,
                       @Nullable VirtualDirectoryImpl parent,
                       @NotNull NewVirtualFileSystem fs) {
    super(id, segment, parent);
    myData = data;
    myFs = fs;
    if (parent != null) {
      registerLink(fs);
    }
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
    updateCaseSensitivityIfUnknown(name);
    VirtualFileSystemEntry result = doFindChild(name, ensureCanonicalName, delegate, isCaseSensitive());

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

  @Nullable // null if there can't be a child with this name, NULL_VIRTUAL_FILE it was adopted
  private VirtualFileSystemEntry doFindChildInArray(@NotNull String name, boolean isCaseSensitive) {
    if (myData.isAdoptedName(name)) return NULL_VIRTUAL_FILE;
    int[] array = myData.myChildrenIds;
    int indexInReal = findIndex(array, name, isCaseSensitive);
    if (indexInReal >= 0) {
      return getVfsData().getFileById(array[indexInReal], this, true);
    }
    return null;
  }

  @Nullable // null if there can't be a child with this name, NULL_VIRTUAL_FILE if cached as absent, the file if found
  private VirtualFileSystemEntry doFindChild(@NotNull String name,
                                             boolean ensureCanonicalName,
                                             @NotNull NewVirtualFileSystem delegate,
                                             boolean isCaseSensitive) {
    if (name.isEmpty()) {
      return null;
    }
    if (!isValid()) {
      return handleInvalidDirectory(null);
    }

    VirtualFileSystemEntry found = doFindChildInArray(name, isCaseSensitive);
    if (found != null) return found;

    if (ensureCanonicalName) {
      String trimmedName = deSlash(name);
      if (trimmedName == null) return null;
      if (!trimmedName.equals(name)) {
        found = doFindChildInArray(trimmedName, isCaseSensitive);
        if (found != null) return found;
        name = trimmedName;
      }
    }

    if (allChildrenLoaded()) {
      return NULL_VIRTUAL_FILE;
    }

    return findInPersistence(name, ensureCanonicalName, delegate, isCaseSensitive);
  }

  @Nullable
  private VirtualFileSystemEntry findInPersistence(@NotNull String name,
                                                   boolean ensureCanonicalName,
                                                   @NotNull NewVirtualFileSystem delegate,
                                                   boolean isCaseSensitive) {
    VirtualFileSystemEntry child;
    synchronized (myData) {
      // maybe another doFindChild() sneaked in the middle
      child = doFindChildInArray(name, isCaseSensitive);
      if (child != null) return child; // including NULL_VIRTUAL_FILE
      if (allChildrenLoaded()) {
        return null;
      }

      // do not extract getId outside the synchronized block since it will cause a concurrency problem.
      ChildInfo childInfo = ourPersistence.findChildInfo(this, name, delegate);
      if (childInfo == null) {
        myData.addAdoptedName(name, isCaseSensitive);
        return null;
      }


      if (ensureCanonicalName) {
        CharSequence persistedName = childInfo.getName();
        if (!Comparing.equal(name, persistedName)) {
          name = persistedName.toString();
          child = doFindChildInArray(name, isCaseSensitive);
          if (child != null) return child;
        }
      }

      int nameId = childInfo.getNameId(); // name can change if file record was created
      int id = childInfo.getId();
      int attributes = ourPersistence.getFileAttributes(id);
      boolean isEmptyDirectory = PersistentFS.isDirectory(attributes) && !ourPersistence.mayHaveChildren(id);

      child = createChild(id, nameId, delegate, attributes, isEmptyDirectory);

      addChild(child);
    }

    if (!child.isDirectory()) {
      // access check should only be called when child is actually added to the parent, otherwise it may break VirtualFilePointers validity
      //noinspection TestOnlyProblems
      VfsRootAccess.assertAccessInTests(child, getFileSystem());
    }

    return child;
  }

  private <T> T handleInvalidDirectory(T empty) {
    if (!ApplicationManager.getApplication().isReadAccessAllowed()) {
      // We can be inside refreshAndFindFileByPath, which must be called outside read action, and
      // throwing an exception doesn't seem a good idea when the callers can't do anything about it
      return empty;
    }
    throw new InvalidVirtualFileAccessException(this);
  }

  @ApiStatus.Internal
  public void setCaseSensitivityFlag(@NotNull FileAttributes.CaseSensitivity sensitivity) {
    VfsData vfsData = getVfsData();
    VfsData.Segment segment = vfsData.getSegment(getId(), false);
    int newFlags = (sensitivity == FileAttributes.CaseSensitivity.UNKNOWN ? 0 : VfsDataFlags.CHILDREN_CASE_SENSITIVITY_CACHED) |
                   (sensitivity == FileAttributes.CaseSensitivity.SENSITIVE ? VfsDataFlags.CHILDREN_CASE_SENSITIVE : 0);
    segment.setFlags(getId(), VfsDataFlags.CHILDREN_CASE_SENSITIVE | VfsDataFlags.CHILDREN_CASE_SENSITIVITY_CACHED, newFlags);
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
    return c == '/' || c == File.separatorChar;
  }

  private VirtualFileSystemEntry @NotNull [] getArraySafely(boolean putToMemoryCache) {
    return myData.getFileChildren(this, putToMemoryCache);
  }

  @NotNull
  @ApiStatus.Internal
  public VirtualFileSystemEntry createChild(@NotNull String name,
                                            int id,
                                            @NotNull NewVirtualFileSystem delegate,
                                            @PersistentFS.Attributes int attributes,
                                            boolean isEmptyDirectory) {
    int nameId = FileNameCache.storeName(name);
    synchronized (myData) {
      return createChild(id, nameId, delegate, attributes, isEmptyDirectory);
    }
  }

  @NotNull
  private VirtualFileSystemEntry createChild(int id,
                                             int nameId,
                                             @NotNull NewVirtualFileSystem delegate,
                                             @PersistentFS.Attributes int attributes,
                                             boolean isEmptyDirectory) {
    FileLoadingTracker.fileLoaded(this, nameId);

    VfsData vfsData = getVfsData();
    VfsData.Segment segment = vfsData.getSegment(id, true);
    boolean isDirectory = PersistentFS.isDirectory(attributes);
    VfsData.initFile(id, segment, nameId, isDirectory ? new VfsData.DirectoryData() : KeyFMap.EMPTY_MAP);
    LOG.assertTrue(!(getFileSystem() instanceof Win32LocalFileSystem));

    VirtualFileSystemEntry child = vfsData.getFileById(id, this, true);
    assert child != null;
    FileAttributes.CaseSensitivity sensitivity = isDirectory ? PersistentFS.areChildrenCaseSensitive(attributes) : FileAttributes.CaseSensitivity.UNKNOWN;
    int newFlags = (PersistentFS.isSymLink(attributes) ? VfsDataFlags.IS_SYMLINK_FLAG : 0) |
                   (PersistentFS.isSpecialFile(attributes) ? VfsDataFlags.IS_SPECIAL_FLAG : 0) |
                   (PersistentFS.isWritable(attributes) ? VfsDataFlags.IS_WRITABLE_FLAG : 0) |
                   (PersistentFS.isHidden(attributes) ? VfsDataFlags.IS_HIDDEN_FLAG : 0) |
                   (sensitivity != FileAttributes.CaseSensitivity.UNKNOWN ? VfsDataFlags.CHILDREN_CASE_SENSITIVITY_CACHED : 0) |
                   (sensitivity == FileAttributes.CaseSensitivity.SENSITIVE ? VfsDataFlags.CHILDREN_CASE_SENSITIVE : 0);
    int relevantFlagsMask = VfsDataFlags.IS_SYMLINK_FLAG |
               VfsDataFlags.IS_SPECIAL_FLAG |
               VfsDataFlags.STRICT_PARENT_HAS_SYMLINK_FLAG |
               VfsDataFlags.IS_WRITABLE_FLAG |
               VfsDataFlags.IS_HIDDEN_FLAG |
               VfsDataFlags.CHILDREN_CASE_SENSITIVE |
               VfsDataFlags.CHILDREN_CASE_SENSITIVITY_CACHED;
    segment.setFlags(id, relevantFlagsMask, newFlags);
    child.updateLinkStatus(this);

    if (delegate.markNewFilesAsDirty()) {
      child.markDirty();
    }
    if (isDirectory && child instanceof VirtualDirectoryImpl && isEmptyDirectory) {
      // when creating empty directory we need to make sure
      // every file created inside will fire "file created" event
      // in order to virtual file pointer manager get those events
      // to update its pointers properly
      // (because currently VirtualFilePointerManager ignores empty directory creation events for performance reasons)
      ((VirtualDirectoryImpl)child).setAllChildrenLoaded();
    }

    return child;
  }

  @Nullable
  private VirtualFileSystemEntry createAndFindChildWithEventFire(@NotNull String name, @NotNull NewVirtualFileSystem delegate) {
    VirtualFile fake = new FakeVirtualFile(this, name);
    FileAttributes attributes = delegate.getAttributes(fake);
    if (attributes == null) return null;
    String realName = delegate.getCanonicallyCasedName(fake);
    boolean isDirectory = attributes.isDirectory();
    boolean isEmptyDirectory = isDirectory && !delegate.hasChildren(fake);
    String symlinkTarget = attributes.isSymLink() ? delegate.resolveSymLink(fake) : null;
    ChildInfo[] children = isEmptyDirectory ? ChildInfo.EMPTY_ARRAY : null;
    VFileCreateEvent event = new VFileCreateEvent(null, this, realName, isDirectory, attributes, symlinkTarget, true, children);
    RefreshQueue.getInstance().processSingleEvent(false, event);
    return findChild(realName);
  }

  private void updateCaseSensitivityIfUnknown(@NotNull String childName) {
    VFilePropertyChangeEvent caseSensitivityEvent = VfsImplUtil.generateCaseSensitivityChangedEventForUnknownCase(this, childName);
    if (caseSensitivityEvent != null) {
      PersistentFSImpl.executeChangeCaseSensitivity(this, (FileAttributes.CaseSensitivity)caseSensitivityEvent.getNewValue());
      // fire event asynchronously to avoid deadlocks with possibly currently-held VFP/Refresh queue locks
      RefreshQueue.getInstance().processSingleEvent(true, caseSensitivityEvent);
      // when the case-sensitivity changes, the "children must be sorted by name" invariant must be restored
      resortChildren();
    }
  }

  private void resortChildren() {
    boolean isCaseSensitive = isCaseSensitive();
    synchronized (myData) {
      VirtualFileSystemEntry[] children = getArraySafely(true);

      Comparator<VirtualFile> byName = (v1, v2) -> compareNames(v1.getNameSequence(), v2.getNameSequence(), isCaseSensitive);

      ContainerUtil.sort(children, byName);
      int[] result = ArrayUtil.newIntArray(children.length);
      for (int i = 0; i < children.length; i++) {
        VirtualFileSystemEntry child = children[i];
        result[i] = child.getId();
      }

      myData.myChildrenIds = result;
      assertConsistency(isCaseSensitive, children, "afterCaseSensitivityChanged", isCaseSensitive);
    }
  }

  @Override
  @Nullable
  public NewVirtualFile refreshAndFindChild(@NotNull String name) {
    return findChild(name, true, true, getFileSystem());
  }

  @Override
  @Nullable
  public NewVirtualFile findChildIfCached(@NotNull String name) {
    VirtualFileSystemEntry found = doFindChildInArray(name, isCaseSensitive());
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
    String[] names = ourPersistence.listPersisted(this);
    NewVirtualFileSystem delegate = PersistentFS.replaceWithNativeFS(getFileSystem());
    for (String name : names) {
      findChild(name, false, false, delegate);
    }
  }

  @Override
  public VirtualFile @NotNull [] getChildren() {
    if (!isValid()) {
      return handleInvalidDirectory(EMPTY_ARRAY);
    }
    if (allChildrenLoaded()) {
      return getArraySafely(true);
    }

    return loadAllChildren();
  }

  private VirtualFile @NotNull [] loadAllChildren() {
    NewVirtualFileSystem delegate = getFileSystem();
    boolean isCaseSensitive = isCaseSensitive();
    synchronized (myData) {
      boolean wasChildrenLoaded = ourPersistence.areChildrenLoaded(this);
      List<? extends ChildInfo> children = ourPersistence.listAll(this);
      int[] result = ArrayUtil.newIntArray(children.size());
      VirtualFile[] files;
      if (children.isEmpty()) {
        files = VirtualFile.EMPTY_ARRAY;
      }
      else {
        files = new VirtualFile[children.size()];
        int[] errorCount = {0};
        children.sort((o1, o2) -> {
          CharSequence name1 = o1.getName();
          CharSequence name2 = o2.getName();
          int cmp = compareNames(name1, name2, isCaseSensitive);
          if (cmp == 0 && name1 != name2) {
            if (errorCount[0]++ == 0) {
              LOG.error(ourPersistence + " returned duplicate file names('" + name1 + "', '" + name2 + "')" +
                        " caseSensitive: " + isCaseSensitive +
                        " SystemInfo.isFileSystemCaseSensitive: " + SystemInfo.isFileSystemCaseSensitive +
                        " isCaseSensitive(): " + isCaseSensitive +
                        " SystemInfo.OS: " + SystemInfo.OS_NAME + " " + SystemInfo.OS_VERSION +
                        " wasChildrenLoaded: " + wasChildrenLoaded +
                        " in the dir: " + this + "; " + children.size() +
                        " children: " + StringUtil.first(children.toString(), 300, true));
            }
            if (!isCaseSensitive) {
              // Sometimes file system rules for case insensitive comparison differ from Java rules.
              // E.g. on NTFS files named \u1E9B (small long S with dot) and \u1E60 (capital S with dot)
              // can coexist while the uppercase for \u1E9B is \u1E60. It's probably because the lower case of
              // \u1E60 is \u1E61 (small S with dot), not \u1E9B. If we encounter such a case,
              // we fallback to case-sensitive comparison, at least to establish some order between these names.
              cmp = compareNames(name1, name2, true);
            }
          }
          return cmp;
        });
        IntSet prevChildren = new IntOpenHashSet(myData.myChildrenIds);
        VfsData vfsData = getVfsData();
        for (int i = 0; i < children.size(); i++) {
          ChildInfo child = children.get(i);
          int id = child.getId();
          assert id > 0 : child;
          result[i] = id;
          prevChildren.remove(id);
          VirtualFileSystemEntry file = vfsData.getFileById(id, this, true);
          if (file == null) {
            int attributes = ourPersistence.getFileAttributes(id);
            boolean isEmptyDirectory = PersistentFS.isDirectory(attributes) && !ourPersistence.mayHaveChildren(id);
            file = createChild(id, child.getNameId(), delegate, attributes, isEmptyDirectory);
          }
          files[i] = file;
        }
        if (!prevChildren.isEmpty()) {
          LOG.error("Loaded child disappeared: " +
                    "parent=" + verboseToString(this) +
                    "; child=" + verboseToString(vfsData.getFileById(prevChildren.iterator().nextInt(), this, true)));
        }
      }

      myData.clearAdoptedNames();
      myData.myChildrenIds = result;
      setAllChildrenLoaded();
      if (CHECK) {
        assertConsistency(isCaseSensitive, children);
      }

      return files;
    }
  }

  private void assertConsistency(boolean isCaseSensitive, Object @NotNull ... details) {
    if (!CHECK || ApplicationManagerEx.isInStressTest()) return;
    int[] childrenIds = myData.myChildrenIds;
    if (childrenIds.length == 0) return;
    VfsData vfsData = getVfsData();
    CharSequence prevName = vfsData.getNameByFileId(childrenIds[0]);
    for (int i = 1; i < childrenIds.length; i++) {
      int id = childrenIds[i];
      int prev = childrenIds[i - 1];
      CharSequence name = vfsData.getNameByFileId(id);
      int cmp = compareNames(name, prevName, isCaseSensitive);
      prevName = name;
      if (cmp <= 0) {
        VirtualFileSystemEntry prevFile = vfsData.getFileById(prev, this, true);
        VirtualFileSystemEntry child = vfsData.getFileById(id, this, true);
        String info = "prevFile.isCaseSensitive()=" + (prevFile == null ? "?" : prevFile.isCaseSensitive()) + ";" +
                      "child.isCaseSensitive()=" + (child == null ? "?" : child.isCaseSensitive()) + ";" +
                      "this.isCaseSensitive()=" + this.isCaseSensitive();

        String message = info + " but in "+this+" the " +verboseToString(prevFile) + "\n is wrongly placed before " + verboseToString(child)+"\n";
        error(message, details);
      }
      synchronized (myData) {
        if (myData.isAdoptedName(name)) {
          List<String> adoptedNames = myData.getAdoptedNames();
          myData.removeAdoptedName(name);
          String message = "In " + verboseToString(this) + " file '" + name + "' is both `child` and `adopted`";
          error(message, "Adopted: " + adoptedNames + ";\n ", details);
        }
      }
    }
  }

  @NotNull
  @NonNls
  private static String verboseToString(@Nullable VirtualFileSystemEntry file) {
    if (file == null) return "null";
    return file + " (name: '" + file.getName()
           + "', " + file.getClass()
           + ", parent: "+file.getParent()
           + "; id: "+file.getId()
           + "; FS: " +file.getFileSystem()
           + "; delegate.attrs: " +file.getFileSystem().getAttributes(file)
           + "; isCaseSensitive: " +file.isCaseSensitive()
           + "; canonical: " +file.getFileSystem().getCanonicallyCasedName(file)
           + ") ";
  }

  private void error(@NonNls String message, @NonNls Object @NotNull ... details) {
    VirtualFileSystemEntry[] array = getArraySafely(true);
    String children = StringUtil.join(array, VirtualDirectoryImpl::verboseToString, "\n");
    String detailsStr = StringUtil.join(ContainerUtil.<Object, Object>map(details, o -> o instanceof Object[] ? Arrays.toString((Object[])o) : o), "\n");
    throw new AssertionError(message + ";\n children: \n" + children + "\nDetails: " + detailsStr);
  }

  @Override
  @Nullable
  public VirtualFileSystemEntry findChild(@NotNull String name) {
    return findChild(name, false, true, getFileSystem());
  }


  @ApiStatus.Internal
  public VirtualFileSystemEntry doFindChildById(int id) {
    int i = ArrayUtil.indexOf(myData.myChildrenIds, id);
    if (i >= 0) {
      VirtualFileSystemEntry fileById = getVfsData().getFileById(id, this, true);
      if (fileById != null) {
        LOG.assertTrue(fileById.getId() == id);
      }
      return fileById;
    }

    String name = ourPersistence.getName(id);
    VirtualFileSystemEntry fileByName = findChild(name, false, false, getFileSystem());
    if (fileByName != null && fileByName.getId() != id) {
      // child with the same name and different id was recreated after refresh session
      // it doesn't make sense to check it earlier because it is executed outside vfs r-w lock
      LOG.assertTrue(FSRecords.isDeleted(id));
      return null;
    }
    return fileByName;
  }

  @Override
  public byte @NotNull [] contentsToByteArray() throws IOException {
    throw new IOException("Cannot get content of directory: " + this);
  }

  // optimisation: works faster than added.forEach(this::addChild)
  @ApiStatus.Internal
  public void createAndAddChildren(@NotNull List<? extends ChildInfo> added,
                                   boolean markAllChildrenLoaded,
                                   @NotNull PairConsumer<? super VirtualFile, ? super ChildInfo> fileCreated) {
    if (added.size() <= 1) {
      //noinspection ForLoopReplaceableByForEach
      for (int i = 0; i < added.size(); i++) {
        ChildInfo info = added.get(i);
        assert info.getId() > 0 : info;
        @PersistentFS.Attributes
        int attributes = info.getFileAttributeFlags();
        boolean isEmptyDirectory = info.getChildren() != null && info.getChildren().length == 0;
        synchronized (myData) {
          int[] oldIds = myData.myChildrenIds;
          if (ArrayUtil.indexOf(oldIds, info.getId()) < 0) {
            VirtualFileSystemEntry file = createChild(info.getId(), info.getNameId(), getFileSystem(), attributes, isEmptyDirectory);
            addChild(file);
            fileCreated.consume(file, info);
          }
        }
      }
      if (markAllChildrenLoaded) {
        setAllChildrenLoaded();
      }
      return;
    }
    // optimization: when there are many children, it's cheaper to
    // merge sorted added and existing lists just like in merge sort
    boolean isCaseSensitive = isCaseSensitive();
    Comparator<ChildInfo> byName = (p1, p2) -> compareNames(p1.getName(), p2.getName(), isCaseSensitive);
    added.sort(byName);

    synchronized (myData) {
      int[] oldIds = myData.myChildrenIds;
      IntList mergedIds = new IntArrayList(oldIds.length + added.size());
      VfsData vfsData = getVfsData();
      List<ChildInfo> existingChildren = new AbstractList<>() {
        @Override
        public ChildInfo get(int index) {
          int id = oldIds[index];
          assert id > 0 : id;
          int nameId = vfsData.getNameId(id);
          return new ChildInfoImpl(id, nameId, null, null, null/*irrelevant here*/);
        }

        @Override
        public int size() {
          return oldIds.length;
        }
      };
      ContainerUtil.processSortedListsInOrder(existingChildren, added, byName, true, (nextInfo, isFileExistsAlready) -> {
        if (!isFileExistsAlready) {
          assert nextInfo.getId() > 0 : nextInfo;
          @PersistentFS.Attributes
          int attributes = nextInfo.getFileAttributeFlags();
          boolean isEmptyDirectory = nextInfo.getChildren() != null && nextInfo.getChildren().length == 0;
          myData.removeAdoptedName(nextInfo.getName());
          VirtualFileSystemEntry file = createChild(nextInfo.getId(), nextInfo.getNameId(), getFileSystem(), attributes, isEmptyDirectory);
          fileCreated.consume(file, nextInfo);
        }
        mergedIds.add(nextInfo.getId());
      });
      myData.myChildrenIds = mergedIds.toIntArray();

      if (markAllChildrenLoaded) {
        setAllChildrenLoaded();
      }
      assertConsistency(isCaseSensitive, added);
    }
  }

  public void addChild(@NotNull VirtualFileSystemEntry child) {
    CharSequence childName = child.getNameSequence();
    boolean isCaseSensitive = isCaseSensitive();
    synchronized (myData) {
      myData.removeAdoptedName(childName);
      int indexInReal = findIndex(myData.myChildrenIds, childName, isCaseSensitive);
      if (indexInReal < 0) {
        int i = -indexInReal -1;
        int id = child.getId();
        assert id > 0 : child + ": " + id;
        myData.myChildrenIds = ArrayUtil.insert(myData.myChildrenIds, i, id);
      }
      // else already stored
      assertConsistency(isCaseSensitive, child, "indexInReal", indexInReal, isCaseSensitive);
    }
  }

  public void removeChild(@NotNull VirtualFile file) {
    boolean isCaseSensitive = isCaseSensitive();
    String name = file.getName();
    synchronized (myData) {
      int indexInReal = findIndex(myData.myChildrenIds, name, isCaseSensitive);
      if (indexInReal >= 0) {
        // there suddenly can be that we ask to add name to adopted whereas it already contained in the real part
        // in this case we should remove it from there
        myData.myChildrenIds = ArrayUtil.remove(myData.myChildrenIds, indexInReal);
      }
      if (!allChildrenLoaded()) {
        myData.addAdoptedName(name, isCaseSensitive);
      }

      assertConsistency(isCaseSensitive, file);
    }
  }

  // optimization: faster than forEach(this::removeChild)
  public void removeChildren(@NotNull IntSet idsToRemove, @NotNull List<? extends CharSequence> namesToRemove) {
    boolean isCaseSensitive = isCaseSensitive();
    synchronized (myData) {
      // remove from array by merging two sorted lists
      int[] newIds = new int[myData.myChildrenIds.length];
      int[] oldIds = myData.myChildrenIds;
      int o = 0;
      for (int oldId : oldIds) {
        if (!idsToRemove.contains(oldId)) {
          assert oldId > 0 : Arrays.toString(oldIds);
          newIds[o++] = oldId;
        }
      }
      if (o != newIds.length) {
        newIds = o == 0 ? ArrayUtilRt.EMPTY_INT_ARRAY : Arrays.copyOf(newIds, o);
      }
      myData.myChildrenIds = newIds;

      if (!allChildrenLoaded()) {
        myData.addAdoptedNames(namesToRemove, isCaseSensitive);
      }

      assertConsistency(isCaseSensitive, namesToRemove);
    }
  }

  @ApiStatus.Internal
  // check if all these names are not existing, remove invalid events from the list
  public void validateChildrenToCreate(@NotNull Collection<VFileCreateEvent> childrenToCreate) {
    if (childrenToCreate.size() <= 1) {
      childrenToCreate.removeIf(event -> !event.isValid());
      return;
    }
    boolean isCaseSensitive = isCaseSensitive();

    Set<CharSequence> existingNames = CollectionFactory.createCharSequenceSet(isCaseSensitive, myData.myChildrenIds.length);
    VfsData vfsData = getVfsData();
    for (int id : myData.myChildrenIds) {
      existingNames.add(vfsData.getNameByFileId(id));
    }
    int id = getId();
    synchronized (myData) {
      existingNames.addAll(FSRecords.listNames(id));

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

  private void validateAgainst(@NotNull Collection<VFileCreateEvent> childrenToCreate, @NotNull Set<? extends CharSequence> existingNames) {
    childrenToCreate.removeIf(event -> {
      String childName = event.getChildName();
      // assume there is no need to canonicalize names in VFileCreateEvent
      return !myData.isAdoptedName(childName) && existingNames.contains(childName);
    });
  }

  public boolean allChildrenLoaded() {
    return myData.allChildrenLoaded();
  }

  private void setAllChildrenLoaded() {
    myData.setAllChildrenLoaded();
  }

  @NotNull
  public List<String> getSuspiciousNames() {
    return myData.getAdoptedNames();
  }

  private int findIndex(int @NotNull [] ids, @NotNull CharSequence name, boolean isCaseSensitive) {
    VfsData vfsData = getVfsData();
    return ObjectUtils.binarySearch(0, ids.length, mid -> compareNames(vfsData.getNameByFileId(ids[mid]), name, isCaseSensitive));
  }

  private static int compareNames(@NotNull CharSequence name1, @NotNull CharSequence name2, boolean isCaseSensitive) {
    int d = name1.length() - name2.length();
    if (d != 0) return d;
    for (int i = 0; i < name1.length(); i++) {
      // com.intellij.openapi.util.text.StringUtil.compare(String,String,boolean) inconsistent
      d = StringUtil.compare(name1.charAt(i), name2.charAt(i), !isCaseSensitive);
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
    return Arrays.asList(getArraySafely(false));
  }

  @Override
  public @NotNull InputStream getInputStream() throws IOException {
    throw new IOException("getInputStream() must not be called against a directory: " + getUrl());
  }

  @Override
  @NotNull
  public OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) throws IOException {
    throw new IOException("getOutputStream() must not be called against a directory: " + getUrl());
  }

  @Override
  public void markDirtyRecursively() {
    markDirty();
    markDirtyRecursivelyInternal();
  }

  // optimisation: do not travel up unnecessary
  private void markDirtyRecursivelyInternal() {
    for (VirtualFileSystemEntry child : getArraySafely(true)) {
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
  protected boolean changeUserMap(@NotNull KeyFMap oldMap, @NotNull KeyFMap newMap) {
    checkLeaks(newMap);
    return myData.changeUserMap(oldMap, UserDataInterner.internUserData(newMap));
  }

  static void checkLeaks(@NotNull KeyFMap newMap) {
    for (Key<?> key : newMap.getKeys()) {
      if (newMap.get(key) instanceof PsiCachedValue) {
        throw new AssertionError("Don't store CachedValue in VFS user data, since it leads to memory leaks");
      }
    }
  }

  @Override
  public boolean isCaseSensitive() {
    return isChildrenCaseSensitivityKnown() ? getFlagInt(VfsDataFlags.CHILDREN_CASE_SENSITIVE) : super.isCaseSensitive();
  }

  /**
   * @return value of CHILDREN_CASE_SENSITIVITY_CACHED bit
   */
  @ApiStatus.Internal
  private boolean isChildrenCaseSensitivityKnown() {
    return getFlagInt(VfsDataFlags.CHILDREN_CASE_SENSITIVITY_CACHED);
  }

  /**
   * @return value of CHILDREN_CASE_SENSITIVE bit, if CHILDREN_CASE_SENSITIVITY_CACHED bit is set or UNKNOWN otherwise
   */
  @ApiStatus.Internal
  @NotNull
  public FileAttributes.CaseSensitivity getChildrenCaseSensitivity() {
    return isChildrenCaseSensitivityKnown() ?
           isCaseSensitive() ? FileAttributes.CaseSensitivity.SENSITIVE : FileAttributes.CaseSensitivity.INSENSITIVE
           : FileAttributes.CaseSensitivity.UNKNOWN;
  }
}