// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diagnostic.ThrottledLogger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.io.FileSystemUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.InvalidVirtualFileAccessException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.ChildInfoImpl;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.openapi.vfs.newvfs.events.ChildInfo;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecordsImpl;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl;
import com.intellij.psi.impl.PsiCachedValue;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.keyFMap.KeyFMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;
import java.util.function.BiConsumer;

import static com.intellij.openapi.vfs.newvfs.events.VFileEvent.REFRESH_REQUESTOR;
import static java.util.concurrent.TimeUnit.SECONDS;

public class VirtualDirectoryImpl extends VirtualFileSystemEntry {
  private static final Logger LOG = Logger.getInstance(VirtualDirectoryImpl.class);
  private static final ThrottledLogger THROTTLED_LOG = new ThrottledLogger(LOG, SECONDS.toMillis(30));

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
  public @NotNull NewVirtualFileSystem getFileSystem() {
    return myFs;
  }

  private @Nullable VirtualFileSystemEntry findChild(@NotNull String name,
                                                     boolean doRefresh,
                                                     boolean ensureCanonicalName,
                                                     @NotNull NewVirtualFileSystem fs) {
    owningPersistentFS().incrementFindChildByNameCount();

    updateCaseSensitivityIfUnknown(name);
    VirtualFileSystemEntry result = doFindChild(name, ensureCanonicalName, fs, isCaseSensitive());

    //noinspection UseVirtualFileEquals
    if (result == NULL_VIRTUAL_FILE) {
      result = doRefresh ? createAndFindChildWithEventFire(name, fs) : null;
    }
    else if (result != null && doRefresh && fs.isDirectory(result) != result.isDirectory()) {
      RefreshQueue.getInstance().refresh(false, false, null, result);
      result = findChild(name, false, ensureCanonicalName, fs);
    }

    return result;
  }

  // null if there can't be a child with this name, NULL_VIRTUAL_FILE it was adopted
  private @Nullable VirtualFileSystemEntry doFindChildInArray(@NotNull String name, boolean isCaseSensitive) {
    if (myData.isAdoptedName(name)) return NULL_VIRTUAL_FILE;
    int[] array = myData.childrenIds;
    int indexInReal = findIndex(array, name, isCaseSensitive);
    if (indexInReal >= 0) {
      return getVfsData().getFileById(array[indexInReal], this, true);
    }
    return null;
  }

  // `null` if there can't be a child with this name,` NULL_VIRTUAL_FILE` if cached as absent, the file if found
  private @Nullable VirtualFileSystemEntry doFindChild(@NotNull String name,
                                                       boolean ensureCanonicalName,
                                                       @NotNull NewVirtualFileSystem fs,
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

    return findInPersistence(name, ensureCanonicalName, fs, isCaseSensitive);
  }

  private @Nullable VirtualFileSystemEntry findInPersistence(@NotNull String name,
                                                             boolean ensureCanonicalName,
                                                             @NotNull NewVirtualFileSystem fs,
                                                             boolean isCaseSensitive) {
    VirtualFileSystemEntry newlyLoadedChild;
    synchronized (myData) {
      // maybe another doFindChild() sneaked in the middle
      VirtualFileSystemEntry existingChild = doFindChildInArray(name, isCaseSensitive);
      if (existingChild != null) return existingChild; // including NULL_VIRTUAL_FILE
      if (allChildrenLoaded()) {
        return null;//all children loaded, but child not found -> not exist
      }

      PersistentFSImpl pfs = owningPersistentFS();
      ChildInfo childInfo = pfs.findChildInfo(this, name, fs);
      if (childInfo == null) {
        myData.addAdoptedName(name, isCaseSensitive);
        return null;
      }

      if (ensureCanonicalName) {
        CharSequence persistedName = childInfo.getName();
        if (!Comparing.equal(name, persistedName)) {
          //lookup again, with persistedName: persistedName _could_ be != name because pfs.findChildInfo() could access
          // actual FS, and FS's rules for file name normalization may be trickier than we implemented in VFS
          existingChild = doFindChildInArray(persistedName.toString(), isCaseSensitive);
          if (existingChild != null) return existingChild;
        }
      }


      int childId = childInfo.getId();
      int childNameId = childInfo.getNameId(); // the name can change if file record was created

      //Lookup a child by id: it is mostly useful for ensureCanonicalName=false, but it seems there are some cases
      // there even with ensureCanonicalName=true a child couldn't be found by name, but _could_ be found by id
      // so let's be sure:
      VirtualFileSystemEntry childById = findCachedChildById(childId);
      if (childById != null) {
        if (ensureCanonicalName) {
          //It is definitely possible for childId to be in this.childrenIds list, but not found by name, if
          // ensureCanonicalName=false -- because of file name normalisation intricacies.
          // But same for ensureCanonicalName=true it is a suspicious case: why didn't we find a child by name then?
          logChildLookupFailure(pfs, childId, childNameId, name);
        }

        return childById;
      }

      int childAttributes = pfs.getFileAttributes(childId);
      //TODO RC: check isDeleted(attributes) before .mayHaveChildren() call,
      //         otherwise 'already deleted' exception is thrown sometimes (EA-933381)?
      boolean isEmptyDirectory = PersistentFS.isDirectory(childAttributes) && !pfs.mayHaveChildren(childId);

      newlyLoadedChild = createChildImpl(childId, childNameId, childAttributes, isEmptyDirectory);
      addChild(newlyLoadedChild);
    }

    if (!newlyLoadedChild.isDirectory()) {
      // access check should only be called when a child is actually added to the parent, otherwise it may break VFP validity
      //noinspection TestOnlyProblems
      VfsRootAccess.assertAccessInTests(newlyLoadedChild, getFileSystem());
    }

    return newlyLoadedChild;
  }

  private void logChildLookupFailure(@NotNull PersistentFSImpl pfs,
                                     int childId,
                                     int childNameId,
                                     @NotNull String childName) {
    FSRecordsImpl vfsPeer = pfs.peer();
    LOG.warn(
      "Child[#" + childId + ", nameId: " + childNameId + "][name='" + vfsPeer.getNameByNameId(childId) + "']" +
      " present in a childrenIds list [" + Arrays.toString(myData.childrenIds) + "], " +
      " but can't be found by name[" + childName + "] even though ensureCanonicalName=true"
    );
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
    if (sensitivity == FileAttributes.CaseSensitivity.UNKNOWN) {
      throw new IllegalArgumentException("invalid argument for " + this + ": " + sensitivity);
    }
    VfsData vfsData = getVfsData();
    VfsData.Segment segment = vfsData.getSegment(getId(), false);
    int newFlags = VfsDataFlags.CHILDREN_CASE_SENSITIVITY_CACHED |
                   (sensitivity == FileAttributes.CaseSensitivity.SENSITIVE ? VfsDataFlags.CHILDREN_CASE_SENSITIVE : 0);
    segment.setFlags(getId(), VfsDataFlags.CHILDREN_CASE_SENSITIVE | VfsDataFlags.CHILDREN_CASE_SENSITIVITY_CACHED, newFlags);
  }

  // removes forward/backslashes from start/end and return trimmed name or null if there are slashes in the middle, or it's empty
  private static String deSlash(@NotNull String name) {
    int startTrimmed = -1;
    int endTrimmed = -1;
    for (int i = 0; i < name.length(); i++) {
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

  @ApiStatus.Internal
  public @NotNull VirtualFileSystemEntry createChild(int fileId,
                                                     int nameId,
                                                     @PersistentFS.Attributes int attributes,
                                                     boolean isEmptyDirectory) {
    synchronized (myData) {
      return createChildImpl(fileId, nameId, attributes, isEmptyDirectory);
    }
  }

  /**
   * 'create' is a bit misleading: method loads child data from persistence into {@link VfsData} in-memory cache
   * Note that loaded entry is _not_ added to a parent's children list.
   */
  //@GuardedBy("myData")
  private VirtualFileSystemEntry createChildImpl(int id, int nameId, @PersistentFS.Attributes int attributes, boolean isEmptyDirectory) {
    FileLoadingTracker.fileLoaded(this, nameId);

    VfsData vfsData = getVfsData();
    VfsData.Segment segment = vfsData.getSegment(id, true);

    boolean isDirectory = PersistentFS.isDirectory(attributes);
    Object fileData = isDirectory ? new VfsData.DirectoryData() : KeyFMap.EMPTY_MAP;
    segment.initFileData(id, fileData, this);

    VirtualFileSystemEntry child = vfsData.getFileById(id, this, true);
    assert child != null;
    FileAttributes.CaseSensitivity sensitivity =
      isDirectory ? PersistentFS.areChildrenCaseSensitive(attributes) : FileAttributes.CaseSensitivity.UNKNOWN;
    int newFlags = (PersistentFS.isSymLink(attributes) ? VfsDataFlags.IS_SYMLINK_FLAG : 0) |
                   (PersistentFS.isSpecialFile(attributes) ? VfsDataFlags.IS_SPECIAL_FLAG : 0) |
                   (PersistentFS.isWritable(attributes) ? VfsDataFlags.IS_WRITABLE_FLAG : 0) |
                   (PersistentFS.isHidden(attributes) ? VfsDataFlags.IS_HIDDEN_FLAG : 0) |
                   (sensitivity != FileAttributes.CaseSensitivity.UNKNOWN ? VfsDataFlags.CHILDREN_CASE_SENSITIVITY_CACHED : 0) |
                   (sensitivity == FileAttributes.CaseSensitivity.SENSITIVE ? VfsDataFlags.CHILDREN_CASE_SENSITIVE : 0) |
                   (PersistentFS.isOfflineByDefault(attributes) ? VfsDataFlags.IS_OFFLINE : 0);
    int relevantFlagsMask = VfsDataFlags.IS_SYMLINK_FLAG |
                            VfsDataFlags.IS_SPECIAL_FLAG |
                            VfsDataFlags.STRICT_PARENT_HAS_SYMLINK_FLAG |
                            VfsDataFlags.IS_WRITABLE_FLAG |
                            VfsDataFlags.IS_HIDDEN_FLAG |
                            VfsDataFlags.CHILDREN_CASE_SENSITIVE |
                            VfsDataFlags.CHILDREN_CASE_SENSITIVITY_CACHED |
                            VfsDataFlags.IS_OFFLINE;
    segment.setFlags(id, relevantFlagsMask, newFlags);
    child.updateLinkStatus(this);

    if (getFileSystem().markNewFilesAsDirty()) {
      child.markDirty();
    }
    if (isDirectory && child instanceof VirtualDirectoryImpl && isEmptyDirectory) {
      // When creating an empty directory, we need to make sure that every file created inside it will fire a "file created" event
      // for virtual file pointer manager to update its pointers properly
      // (because currently VirtualFilePointerManager ignores empty directory creation events for performance reasons).
      ((VirtualDirectoryImpl)child).setAllChildrenLoaded();
    }

    return child;
  }

  private @Nullable VirtualFileSystemEntry createAndFindChildWithEventFire(@NotNull String name, @NotNull NewVirtualFileSystem fs) {
    VirtualFile fake = new FakeVirtualFile(this, name);
    FileAttributes attributes = fs.getAttributes(fake);
    if (attributes == null) return null;
    String realName = fs.getCanonicallyCasedName(fake);
    boolean isDirectory = attributes.isDirectory();
    boolean isEmptyDirectory = isDirectory && !fs.hasChildren(fake);
    String symlinkTarget = attributes.isSymLink() ? fs.resolveSymLink(fake) : null;
    ChildInfo[] children = isEmptyDirectory ? ChildInfo.EMPTY_ARRAY : null;
    var event = new VFileCreateEvent(REFRESH_REQUESTOR, this, realName, isDirectory, attributes, symlinkTarget, children);
    RefreshQueue.getInstance().processEvents(false, List.of(event));
    return findChild(realName);
  }

  private void updateCaseSensitivityIfUnknown(@NotNull String childName) {
    VFilePropertyChangeEvent caseSensitivityEvent = generateCaseSensitivityChangedEventForUnknownCase(this, childName);
    if (caseSensitivityEvent != null) {

      changeCaseSensitivity(this, (FileAttributes.CaseSensitivity)caseSensitivityEvent.getNewValue());
      // fire event asynchronously to avoid deadlocks with possibly currently held VFP/Refresh queue locks
      RefreshQueue.getInstance().processEvents(true, List.of(caseSensitivityEvent));
      // when the case-sensitivity changes, the "children must be sorted by name" invariant must be restored
      resortChildren();
    }
    else if (getChildrenCaseSensitivity() == FileAttributes.CaseSensitivity.UNKNOWN) {
      // cache case sensitivity anyway even when we failed to read it from the disk, to avoid freezes trying to re-read it constantly
      setCaseSensitivityFlag(
        getFileSystem().isCaseSensitive() ? FileAttributes.CaseSensitivity.SENSITIVE : FileAttributes.CaseSensitivity.INSENSITIVE);
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

      myData.childrenIds = result;
      assertConsistency(isCaseSensitive, children, "afterCaseSensitivityChanged", isCaseSensitive);
    }
  }

  @Override
  public @Nullable NewVirtualFile refreshAndFindChild(@NotNull String name) {
    return findChild(name, true, true, getFileSystem());
  }

  @Override
  public @Nullable NewVirtualFile findChildIfCached(@NotNull String name) {
    VirtualFileSystemEntry found = doFindChildInArray(name, isCaseSensitive());
    //noinspection UseVirtualFileEquals
    return found == NULL_VIRTUAL_FILE ? null : found;
  }

  @Override
  public @NotNull Iterable<VirtualFile> iterInDbChildren() {
    if (!owningPersistentFS().wereChildrenAccessed(this)) {
      return Collections.emptyList();
    }

    if (owningPersistentFS().areChildrenLoaded(this)) {
      return Arrays.asList(getChildren()); // may load VFS from other projects
    }

    loadPersistedChildren();

    return getCachedChildren();
  }

  @Override
  public @NotNull Iterable<VirtualFile> iterInDbChildrenWithoutLoadingVfsFromOtherProjects() {
    if (!owningPersistentFS().wereChildrenAccessed(this)) {
      return Collections.emptyList();
    }
    if (!owningPersistentFS().areChildrenLoaded(this)) {
      loadPersistedChildren();
    }
    return getCachedChildren();
  }

  private void loadPersistedChildren() {
    String[] names = owningPersistentFS().listPersisted(this);
    NewVirtualFileSystem fs = getFileSystem();
    for (String name : names) {
      findChild(name, false, false, fs);
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
    boolean isCaseSensitive = isCaseSensitive();
    synchronized (myData) {
      boolean wasChildrenLoaded = owningPersistentFS().areChildrenLoaded(this);
      List<? extends ChildInfo> children = owningPersistentFS().listAll(this);
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
              LOG.error(owningPersistentFS() + " returned duplicate file names('" + name1 + "', '" + name2 + "')" +
                        " caseSensitive: " + isCaseSensitive +
                        " SystemInfo.isFileSystemCaseSensitive: " + SystemInfo.isFileSystemCaseSensitive +
                        " isCaseSensitive(): " + isCaseSensitive +
                        " SystemInfo.OS: " + SystemInfo.OS_NAME + " " + SystemInfo.OS_VERSION +
                        " wasChildrenLoaded: " + wasChildrenLoaded +
                        " in the dir: " + this + "; " + children.size() +
                        " children: " + StringUtil.first(children.toString(), 300, true));
            }
            if (!isCaseSensitive) {
              // Sometimes file system rules for case-insensitive comparison differ from Java rules.
              // E.g., on NTFS files named 'ẛ' (small long S with dot) and 'Ṡ' (capital S with dot) can coexist
              // despite the uppercase for 'ẛ' being 'Ṡ' - probably because the lower case of 'Ṡ' is 'ṡ' (small S with dot), not 'ẛ'.
              // When encountering such a case, we fall back to case-sensitive comparison to establish some order between these names.
              cmp = compareNames(name1, name2, true);
            }
          }
          return cmp;
        });
        IntSet prevChildren = new IntOpenHashSet(myData.childrenIds);
        VfsData vfsData = getVfsData();
        for (int i = 0; i < children.size(); i++) {
          ChildInfo child = children.get(i);
          int id = child.getId();
          assert id > 0 : child;
          result[i] = id;
          prevChildren.remove(id);
          VirtualFileSystemEntry file = vfsData.getFileById(id, this, true);
          if (file == null) {
            int attributes = owningPersistentFS().getFileAttributes(id);
            boolean isEmptyDirectory = PersistentFS.isDirectory(attributes) && !owningPersistentFS().mayHaveChildren(id);
            file = createChildImpl(id, child.getNameId(), attributes, isEmptyDirectory);
          }
          files[i] = file;
        }
        if (!prevChildren.isEmpty()) {
          var missing = vfsData.getFileById(prevChildren.iterator().nextInt(), this, true);
          LOG.error("Loaded child disappeared: parent=" + verboseToString(this) + "; child=" + verboseToString(missing));
        }
      }

      myData.clearAdoptedNames();
      myData.childrenIds = result;
      setAllChildrenLoaded();
      if (CHECK) {
        assertConsistency(isCaseSensitive, children);
      }

      return files;
    }
  }

  private void assertConsistency(boolean isCaseSensitive, Object @NotNull ... details) {
    if (!CHECK || ApplicationManagerEx.isInStressTest()) return;
    int[] childrenIds = myData.childrenIds;
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
        String info = "prevFile.isCaseSensitive()=" + (prevFile == null ? "?" : prevFile.isCaseSensitive()) + ';' +
                      "child.isCaseSensitive()=" + (child == null ? "?" : child.isCaseSensitive()) + ';' +
                      "this.isCaseSensitive()=" + this.isCaseSensitive();
        String message =
          info + " but in " + this + " the " + verboseToString(prevFile) + "\n is wrongly placed before " + verboseToString(child) + '\n';
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

  private static String verboseToString(@Nullable VirtualFileSystemEntry file) {
    return file == null ? "null" :
           file +
           " (name: '" + file.getName() + "'" +
           ", " + file.getClass() +
           ", parent: " + file.getParent() +
           ", id: " + file.getId() +
           ", FS: " + file.getFileSystem() +
           ", fs.attrs: " + file.getFileSystem().getAttributes(file) +
           ", isCaseSensitive: " + file.isCaseSensitive() +
           ", canonical: " + file.getFileSystem().getCanonicallyCasedName(file) +
           ')';
  }

  private void error(String message, Object... details) {
    var builder = new StringBuilder().append(message).append("\n--- children ---");
    for (var child : getArraySafely(true)) builder.append('\n').append(verboseToString(child));
    builder.append("--- details ---");
    for (var o : details) builder.append('\n').append(o instanceof Object[] ? Arrays.toString((Object[])o) : o.toString());
    throw new AssertionError(builder.toString());
  }

  @Override
  public @Nullable VirtualFileSystemEntry findChild(@NotNull String name) {
    return findChild(name, false, true, getFileSystem());
  }

  @ApiStatus.Internal
  public VirtualFileSystemEntry doFindChildById(int id) {
    VirtualFileSystemEntry existingChild = findCachedChildById(id);
    if (existingChild != null) return existingChild;

    //We come here only from PersistentFSImpl.findFileById(), on a descend phase, there we resolve fileIds to
    // VFiles. Hence, it must be a child with childId -- because 'this' was collected as .parent during an
    // ascend phase. If that is not the case -- either something was changed in between (e.g., children were
    // refreshed), or there is an inconsistency in VFS (e.g., children and .parent fall out of sync):

    //So the branch below is almost surely 'child just not loaded yet'

    PersistentFSImpl persistence = owningPersistentFS();
    String name = persistence.getName(id);
    //RC: why ensureCanonicalName=false?
    VirtualFileSystemEntry fileByName = findChild(name, /*refresh: */ false, /*ensureCanonicalName: */ false, getFileSystem());
    if (fileByName != null && fileByName.getId() != id) {
      // a child with the same name and different ID was recreated after a refresh session -
      // it doesn't make sense to check it earlier because it is executed outside the VFS' read/write lock
      boolean deleted = persistence.peer().isDeleted(id);
      if (!deleted) {
        THROTTLED_LOG.info(() -> {
          int parentId = persistence.peer().getParent(id);
          IntOpenHashSet childrenInPersistence = new IntOpenHashSet(persistence.peer().listIds(id));
          IntOpenHashSet childrenInMemory = new IntOpenHashSet(myData.childrenIds);
          int[] childrenNotInPersistent = childrenInMemory.intStream()
            .filter(childId -> !childrenInPersistence.contains(childId))
            .toArray();
          int[] childrenNotInMemory = childrenInPersistence.intStream()
            .filter(childId -> !childrenInMemory.contains(childId))
            .toArray();
          return "FSRecords(id: " + id + ", parentId: " + parentId + ", name: '" + name + "', !deleted), " +
                 "but VDI.findChild(" + name + ")=" + fileByName + " with different id(=" + fileByName.getId() + ")" +
                 "\n\tchildrenInMemory: " + childrenInMemory.size() + ", childrenInPersistence: " + childrenInPersistence.size() + ", " +
                 "\n\tdiff(" + childrenNotInPersistent.length + " vs " + childrenNotInMemory.length + ")" +
                 "\n\tchildrenInMemory (up to 64): \n" +
                 Arrays.stream(myData.childrenIds)
                   .limit(64)
                   .mapToObj(childId -> "\n\t" + childId + ": '" + persistence.getName(childId) + "'")
                   .toList();
        });
      }
      return null;
    }
    return fileByName;
  }

  private @Nullable VirtualFileSystemEntry findCachedChildById(int childId) {
    int i = ArrayUtil.indexOf(myData.childrenIds, childId);
    if (i >= 0) {
      VirtualFileSystemEntry fileById = getVfsData().getFileById(childId, this, true);
      if (fileById != null) {
        if (fileById.getId() != childId) {
          LOG.error("getFileById(" + childId + ") returns " + fileById + " with different id(=" + fileById.getId() + ")");
        }
      }
      return fileById;
    }
    return null;
  }

  @Override
  public byte @NotNull [] contentsToByteArray() throws IOException {
    throw new IOException("Cannot get content of directory: " + this);
  }

  // optimization: works faster than added.forEach(this::addChild)
  @ApiStatus.Internal
  public void createAndAddChildren(@NotNull List<ChildInfo> added,
                                   boolean markAllChildrenLoaded,
                                   @NotNull BiConsumer<? super VirtualFile, ? super ChildInfo> callback) {
    if (added.size() <= 1) {
      for (int i = 0; i < added.size(); i++) {
        ChildInfo info = added.get(i);
        assert info.getId() > 0 : info;
        @SuppressWarnings("MagicConstant") @PersistentFS.Attributes int attributes = info.getFileAttributeFlags();
        boolean isEmptyDirectory = info.getChildren() != null && info.getChildren().length == 0;
        synchronized (myData) {
          int[] oldIds = myData.childrenIds;
          if (ArrayUtil.indexOf(oldIds, info.getId()) < 0) {
            VirtualFileSystemEntry file = createChildImpl(info.getId(), info.getNameId(), attributes, isEmptyDirectory);
            addChild(file);
            callback.accept(file, info);
          }
        }
      }
      if (markAllChildrenLoaded) {
        setAllChildrenLoaded();
      }
      return;
    }
    // when there are many children, it's cheaper to merge sorted lists just like in merge sort
    boolean isCaseSensitive = isCaseSensitive();
    Comparator<ChildInfo> byName = (p1, p2) -> compareNames(p1.getName(), p2.getName(), isCaseSensitive);
    added.sort(byName);

    synchronized (myData) {
      int[] oldIds = myData.childrenIds;
      IntList mergedIds = new IntArrayList(oldIds.length + added.size());
      FSRecordsImpl vfsPeer = owningPersistentFS().peer();

      List<ChildInfo> existingChildren = new AbstractList<>() {
        @Override
        public ChildInfo get(int index) {
          int id = oldIds[index];
          assert id > 0 : id;
          int nameId = vfsPeer.getNameIdByFileId(id);
          return new ChildInfoImpl(id, nameId, null, null, null/*irrelevant here*/);
        }

        @Override
        public int size() {
          return oldIds.length;
        }
      };
      ContainerUtil.processSortedListsInOrder(existingChildren, added, byName, /*mergeEqualItems: */ true, (nextInfo, mergeResult) -> {
        //TODO RC: this branch differs from (added.size=1) branch above in many aspects. The most noticeable are
        //         comparison: we merge existing/added lists by-name here, but we look up existing fileId above.
        //         This seems suspicious, and I'm not sure that consequences of the difference could be.
        //         Another difference is that here we call callback.accept() first, and update .childrenIds list with
        //         the new files afterwards, but in 1-item branch above we add new file to .childrenIds list first,
        //         and call the callback afterwards.
        if (mergeResult == ContainerUtil.MergeResult.COPIED_FROM_LIST2) {
          assert nextInfo.getId() > 0 : nextInfo;
          @SuppressWarnings("MagicConstant") @PersistentFS.Attributes int attributes = nextInfo.getFileAttributeFlags();
          boolean isEmptyDirectory = nextInfo.getChildren() != null && nextInfo.getChildren().length == 0;
          myData.removeAdoptedName(nextInfo.getName());
          VirtualFileSystemEntry file = createChildImpl(nextInfo.getId(), nextInfo.getNameId(), attributes, isEmptyDirectory);
          callback.accept(file, nextInfo);
        }
        mergedIds.add(nextInfo.getId());
      });
      myData.childrenIds = mergedIds.toIntArray();

      if (markAllChildrenLoaded) {
        setAllChildrenLoaded();
      }
      assertConsistency(isCaseSensitive, added);
    }
  }

  /** does nothing if child.id is already in myData.childrenIds list */
  public void addChild(@NotNull VirtualFileSystemEntry child) {
    CharSequence childName = child.getNameSequence();
    boolean isCaseSensitive = isCaseSensitive();
    synchronized (myData) {
      myData.removeAdoptedName(childName);
      int indexInReal = findIndex(myData.childrenIds, childName, isCaseSensitive);
      if (indexInReal < 0) {
        int i = -indexInReal - 1;
        int id = child.getId();
        assert id > 0 : child + ": " + id;
        myData.childrenIds = ArrayUtil.insert(myData.childrenIds, i, id);
      }
      // else already stored
      assertConsistency(isCaseSensitive, child, "indexInReal", indexInReal, isCaseSensitive);
    }
  }

  public void removeChild(@NotNull VirtualFile file) {
    boolean isCaseSensitive = isCaseSensitive();
    String name = file.getName();
    synchronized (myData) {
      int indexInReal = findIndex(myData.childrenIds, name, isCaseSensitive);
      if (indexInReal >= 0) {
        // it can be that we ask to add a name to the adopted list whereas it is already contained in the real part -
        // in this case, we should remove it from the latter
        myData.childrenIds = ArrayUtil.remove(myData.childrenIds, indexInReal);
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
      // remove from the array by merging two sorted lists
      int[] newIds = new int[myData.childrenIds.length];
      int[] oldIds = myData.childrenIds;
      int o = 0;
      for (int oldId : oldIds) {
        if (!idsToRemove.contains(oldId)) {
          assert oldId > 0 : Arrays.toString(oldIds);
          newIds[o++] = oldId;
        }
      }
      if (o != newIds.length) {
        newIds = o == 0 ? ArrayUtil.EMPTY_INT_ARRAY : Arrays.copyOf(newIds, o);
      }
      myData.childrenIds = newIds;

      if (!allChildrenLoaded()) {
        myData.addAdoptedNames(namesToRemove, isCaseSensitive);
      }

      assertConsistency(isCaseSensitive, namesToRemove);
    }
  }

  // checking if all these names are not existing, removing invalid events from the list
  @ApiStatus.Internal
  public void validateChildrenToCreate(@NotNull Collection<VFileCreateEvent> childrenToCreate) {
    if (childrenToCreate.size() <= 1) {
      childrenToCreate.removeIf(event -> !event.isValid());
      return;
    }
    boolean isCaseSensitive = isCaseSensitive();

    Set<CharSequence> existingNames = CollectionFactory.createCharSequenceSet(isCaseSensitive, myData.childrenIds.length);
    VfsData vfsData = getVfsData();
    for (int id : myData.childrenIds) {
      existingNames.add(vfsData.getNameByFileId(id));
    }
    int id = getId();
    synchronized (myData) {
      existingNames.addAll(owningPersistentFS().peer().listNames(id));

      validateAgainst(childrenToCreate, existingNames);

      if (!childrenToCreate.isEmpty() && !allChildrenLoaded()) {
        // findChild asks FS when failed to locate a child, and so should we
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

  public @NotNull List<String> getSuspiciousNames() {
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
  public @NotNull List<VirtualFile> getCachedChildren() {
    return Arrays.asList(getArraySafely(false));
  }

  @Override
  public @NotNull InputStream getInputStream() throws IOException {
    throw new IOException("getInputStream() must not be called against a directory: " + getUrl());
  }

  @Override
  public @NotNull OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) throws IOException {
    throw new IOException("getOutputStream() must not be called against a directory: " + getUrl());
  }

  @Override
  public void markDirtyRecursively() {
    markDirty();
    markDirtyRecursivelyInternal();
  }

  // optimization: do not travel up unnecessary
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
    myData.userMap = map;
  }

  @Override
  protected @NotNull KeyFMap getUserMap() {
    return myData.userMap;
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
   * @return the value of CHILDREN_CASE_SENSITIVITY_CACHED bit
   */
  @ApiStatus.Internal
  private boolean isChildrenCaseSensitivityKnown() {
    return getFlagInt(VfsDataFlags.CHILDREN_CASE_SENSITIVITY_CACHED);
  }

  /**
   * @return the value of CHILDREN_CASE_SENSITIVE bit, if CHILDREN_CASE_SENSITIVITY_CACHED bit is set, or UNKNOWN otherwise
   */
  @ApiStatus.Internal
  public @NotNull FileAttributes.CaseSensitivity getChildrenCaseSensitivity() {
    return isChildrenCaseSensitivityKnown() ?
           isCaseSensitive() ? FileAttributes.CaseSensitivity.SENSITIVE : FileAttributes.CaseSensitivity.INSENSITIVE
                                            : FileAttributes.CaseSensitivity.UNKNOWN;
  }

  static <T, E extends Throwable> T runUnderAllLocksAcquired(@NotNull ThrowableComputable<T, E> lambda,
                                                             @NotNull VirtualDirectoryImpl dir1,
                                                             @NotNull VirtualDirectoryImpl dir2) throws E {
    Object lock1 = dir1.myData;
    Object lock2 = dir2.myData;
    //order locks acquisition by their identity hash code:
    if (System.identityHashCode(lock1) <= System.identityHashCode(lock2)) {
      synchronized (lock1) {
        synchronized (lock2) {
          return lambda.compute();
        }
      }
    }
    else {
      synchronized (lock2) {
        synchronized (lock1) {
          return lambda.compute();
        }
      }
    }
  }

  /**
   * If the {@code parent} case-sensitivity flag is still not known, try to determine it via {@link FileSystemUtil#readParentCaseSensitivity(File)}.
   * If this flag read successfully, prepare to fire the {@link VirtualFile#PROP_CHILDREN_CASE_SENSITIVITY} event
   * (but only if this flag is different from the FS-default case-sensitivity to avoid too many unnecessary events:
   * see {@link VirtualFileSystem#isCaseSensitive()}).
   * Otherwise, return null.
   */
  @ApiStatus.Internal
  public static VFilePropertyChangeEvent generateCaseSensitivityChangedEventForUnknownCase(@NotNull VirtualFile parent,
                                                                                           @NotNull String childName) {
    if (((VirtualDirectoryImpl)parent).getChildrenCaseSensitivity() != FileAttributes.CaseSensitivity.UNKNOWN) {
      return null;
    }
    FileAttributes.CaseSensitivity sensitivity = FileSystemUtil.readParentCaseSensitivity(new File(parent.getPath(), childName));
    return generateCaseSensitivityChangedEvent(parent, sensitivity);
  }

  @ApiStatus.Internal
  public static VFilePropertyChangeEvent generateCaseSensitivityChangedEvent(@NotNull VirtualFile dir,
                                                                             @NotNull FileAttributes.CaseSensitivity actualCaseSensitivity) {
    if (actualCaseSensitivity != FileAttributes.CaseSensitivity.UNKNOWN) {
      if (dir.getFileSystem().isCaseSensitive() != (actualCaseSensitivity == FileAttributes.CaseSensitivity.SENSITIVE)) {
        // fire only when the new case sensitivity is different from the default FS sensitivity,
        // because only in that case the file.isCaseSensitive() value could change
        return new VFilePropertyChangeEvent(REFRESH_REQUESTOR, dir, VirtualFile.PROP_CHILDREN_CASE_SENSITIVITY,
                                            FileAttributes.CaseSensitivity.UNKNOWN, actualCaseSensitivity);
      }
      else {
        changeCaseSensitivity(dir, actualCaseSensitivity);
      }
    }
    return null;
  }

  private static void changeCaseSensitivity(@NotNull VirtualFile vFile,
                                            @NotNull FileAttributes.CaseSensitivity newValue) {
    ((PersistentFSImpl)PersistentFS.getInstance()).executeChangeCaseSensitivity(
      vFile,
      newValue
    );
  }
}
