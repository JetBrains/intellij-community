// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diagnostic.ThrottledLogger;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.io.FileAttributes.CaseSensitivity;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.InvalidVirtualFileAccessException;
import com.intellij.openapi.vfs.VirtualFile;
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
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSRecordAccessor;
import com.intellij.psi.impl.PsiCachedValue;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.keyFMap.KeyFMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;
import java.util.function.BiConsumer;

import static com.intellij.openapi.vfs.newvfs.events.VFileEvent.REFRESH_REQUESTOR;
import static com.intellij.util.SystemProperties.getBooleanProperty;
import static com.intellij.util.SystemProperties.getIntProperty;
import static java.util.concurrent.TimeUnit.SECONDS;

@ApiStatus.Internal
public class VirtualDirectoryImpl extends VirtualFileSystemEntry {
  private static final Logger LOG = Logger.getInstance(VirtualDirectoryImpl.class);
  private static final ThrottledLogger THROTTLED_LOG = new ThrottledLogger(LOG, SECONDS.toMillis(30));

  private static final boolean CHECK_CONSISTENCY = ApplicationManager.getApplication().isUnitTestMode()
                                                   && !ApplicationManagerEx.isInStressTest();

  /**
   * Use linear (bruteforce) search for sorted children if size <= this threshold, use binary search by-name, if size is larger.
   * <p>
   * Children ids ({@link com.intellij.openapi.vfs.newvfs.impl.VfsData.DirectoryData#children} are either unsorted, or sorted
   * _by (file)name_. It is natural to use binary-search to search in a sorted array, but really even if children ids _are_
   * sorted by-name -- it may be still faster to look for given childId with linear scan, because scanning through int[] is
   * quite fast on modern CPUs, while binary search requires costly (String,String) comparison -- especially costly for
   * case-insensitive directories.
   * Value=64 is chosen arbitrary, by my intuition.
   */
  private static final int LINEAR_SEARCH_THRESHOLD = getIntProperty("VirtualDirectoryImpl.LINEAR_SEARCH_THRESHOLD", 64);
  /**
   * If true: trust {@link #findChildById(int)} callers that supplied childId is indeed an id of existing child.
   * I.e. don't load the children from persistence to check is childId really in .children.
   * Less expensive, but more prone to errors if e.g. orphan records are present in VFS (sometimes they do).
   * If false: don't trust the caller, check that childId really belongs to .children (loads children from persistence = more expensive)
   */
  private static final boolean TRUST_FIND_CHILD_BY_ID_CALLERS = getBooleanProperty(
    "VirtualDirectoryImpl.TRUST_FIND_CHILD_BY_ID_CALLERS", false
  );

  /**
   * Actual directory data is stored here, VirtualDirectoryImpl instance is just a lightweight wrapper around it, and
   * it could be >=1 VirtualDirectoryImpl instances wrapping the same shared directoryData.
   * Field is made package-local-visible _only_ for building diagnostic info on errors
   */
  final VfsData.DirectoryData directoryData;
  private final NewVirtualFileSystem fileSystem;

  @VisibleForTesting
  @ApiStatus.Internal
  public VirtualDirectoryImpl(int id,
                              @NotNull VfsData.Segment segment,
                              @NotNull VfsData.DirectoryData directoryData,
                              @Nullable VirtualDirectoryImpl parent,
                              @NotNull NewVirtualFileSystem fileSystem) {
    super(id, segment, parent);
    this.directoryData = directoryData;
    this.fileSystem = fileSystem;
    if (parent != null) {
      registerLink(fileSystem);
    }
  }

  @Override
  public boolean isDirectory() {
    return true;
  }

  @Override
  public @NotNull NewVirtualFileSystem getFileSystem() {
    return fileSystem;
  }

  private @Nullable VirtualFileSystemEntry findChild(@NotNull String name,
                                                     boolean doRefresh,
                                                     boolean ensureCanonicalName) {
    owningPersistentFS().incrementFindChildByNameCount();

    //MAYBE RC: call it only if doRefresh=true?
    updateCaseSensitivityIfUnknown(name);

    VirtualFileSystemEntry result = findChildImpl(name, ensureCanonicalName, isCaseSensitive());

    //noinspection UseVirtualFileEquals
    if (result == NULL_VIRTUAL_FILE) {
      result = doRefresh ? createAndFindChildWithEventFire(name) : null;
    }
    else if (result != null && doRefresh && fileSystem.isDirectory(result) != result.isDirectory()) {
      RefreshQueue.getInstance().refresh(false, false, null, result);
      result = findChild(name, false, ensureCanonicalName);
    }

    return result;
  }

  /**
   * @return child with given childName (according to isCaseSensitive), among already cached .children.
   * null if there is no child with this name, NULL_VIRTUAL_FILE it was adopted
   */
  private @Nullable VirtualFileSystemEntry findInCachedChildren(@NotNull String childName, boolean isCaseSensitive) {
    if (directoryData.isAdoptedName(childName)) return NULL_VIRTUAL_FILE;
    VfsData.ChildrenIds sortedChildren = ensureChildrenSorted(isCaseSensitive);
    int indexByName = findIndexByName(sortedChildren, childName, isCaseSensitive);
    if (indexByName >= 0) {
      return getVfsData().getFileById(sortedChildren.id(indexByName), this, /*putInCache: */ true);
    }
    return null;
  }

  private VfsData.ChildrenIds ensureChildrenSorted(boolean isCaseSensitive) {
    VfsData.ChildrenIds children = directoryData.children;
    if (children.isSorted()) {
      return children;
    }

    synchronized (directoryData) {
      children = directoryData.children;
      if (children.isSorted()) {
        return children;
      }

      return sortChildrenByName(children, isCaseSensitive);
    }
  }

  /**
   * @return the child, if there is a child with given name (accounting for isCaseSensitive and ensureCanonicalName).
   * `null` if is no child with given name, ` NULL_VIRTUAL_FILE` if cached as absent (='adopted').
   * Lookups among: cached children, VFS-persisted children, and actual children in file-system backed this directory.
   */
  private @Nullable VirtualFileSystemEntry findChildImpl(@NotNull String name,
                                                         boolean ensureCanonicalName,
                                                         boolean isCaseSensitive) {
    if (name.isEmpty()) {
      return null;
    }
    if (!isValid()) {
      return handleInvalidDirectory(null);
    }

    VirtualFileSystemEntry found = findInCachedChildren(name, isCaseSensitive);
    if (found != null) return found;

    if (ensureCanonicalName) {
      String trimmedName = deSlash(name);
      if (trimmedName == null) return null;
      if (!trimmedName.equals(name)) {
        found = findInCachedChildren(trimmedName, isCaseSensitive);
        if (found != null) return found;
        name = trimmedName;
      }
    }

    if (allChildrenLoaded()) {
      return NULL_VIRTUAL_FILE;
    }


    return findInPersistence(name, ensureCanonicalName, isCaseSensitive);
  }

  /**
   * @return the child with given name, or null, if it doesn't exist. Child is looked up among cached children, among
   * VFS-persistent children, and finally in the actual file-system backing this directory.
   * If the child is found outside the cache -- it is loaded and cached.
   */
  private @Nullable VirtualFileSystemEntry findInPersistence(@NotNull String name,
                                                             boolean ensureCanonicalName,
                                                             boolean isCaseSensitive) {
    VirtualFileSystemEntry newlyLoadedChild;
    synchronized (directoryData) {
      // maybe another doFindChild() sneaked in the middle
      VirtualFileSystemEntry existingChild = findInCachedChildren(name, isCaseSensitive);
      if (existingChild != null) return existingChild; // including NULL_VIRTUAL_FILE
      if (allChildrenLoaded()) {
        return null;//all children loaded, but child not found -> not exist
      }

      PersistentFSImpl pfs = owningPersistentFS();
      ChildInfo childInfo = pfs.findChildInfo(this, name, fileSystem);
      if (childInfo == null) {
        directoryData.addAdoptedName(name, isCaseSensitive);
        return null;
      }

      if (ensureCanonicalName) {
        CharSequence persistedName = childInfo.getName();
        if (!Comparing.equal(name, persistedName)) {
          //lookup again, with persistedName: persistedName _could_ be != name because pfs.findChildInfo() could access
          // actual FS, and FS's rules for file name normalization may be trickier than we implemented in VFS
          existingChild = findInCachedChildren(persistedName.toString(), isCaseSensitive);
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
          //It is definitely possible for childId to be in this.children list, but not found by name, if
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

  /**
   * Updates this directory case-sensitivity to new sensitivity, and set case-sensitivity-cached flag
   * to true.
   * Updates only in-memory values, does NOT update VFS persistent structures (see {@link PersistentFSImpl#executeChangeCaseSensitivity(VirtualDirectoryImpl, CaseSensitivity)}
   * for that).
   * If case-sensitivity value is actually changed -- re-order the children accordingly
   */
  @ApiStatus.Internal
  public void setCaseSensitivityFlag(boolean newIsCaseSensitive) {
    CaseSensitivity oldCaseSensitivity = getChildrenCaseSensitivity();
    if (oldCaseSensitivity.isUnknown()
        || oldCaseSensitivity.isSensitive() != newIsCaseSensitive) {
      VfsData vfsData = getVfsData();
      VfsData.Segment segment = vfsData.getSegment(getId(), false);
      int newFlags = VfsDataFlags.CHILDREN_CASE_SENSITIVITY_CACHED |
                     (newIsCaseSensitive ? VfsDataFlags.CHILDREN_CASE_SENSITIVE : 0);
      segment.setFlags(getId(), VfsDataFlags.CHILDREN_CASE_SENSITIVE | VfsDataFlags.CHILDREN_CASE_SENSITIVITY_CACHED, newFlags);

      //children are sorted by name => case-sensitivity change requires re-sorting:
      resortChildren();
      //Not only children, but vfsData.adoptedNames also relies on case-sensitivity:
      synchronized (directoryData) {
        List<String> adoptedNames = directoryData.getAdoptedNames();
        directoryData.clearAdoptedNames();
        directoryData.addAdoptedNames(adoptedNames, newIsCaseSensitive);
      }
    }
  }

  @ApiStatus.Internal
  public @NotNull VirtualFileSystemEntry createChildIfNotExist(int fileId,
                                                               int nameId,
                                                               @PersistentFS.Attributes int attributes,
                                                               boolean isEmptyDirectory) {
    synchronized (directoryData) {
      //check is it already initialized:
      //MAYBE RC: getVfsData().hasLoadedFile() is probably a better way to check this?
      VirtualFileSystemEntry entry = getVfsData().getFileById(fileId, this, /*putInCache: */ true);
      if (entry != null) {
        return entry;
      }
      return createChildImpl(fileId, nameId, attributes, isEmptyDirectory);
    }
  }

  /**
   * 'create' is a bit misleading: method instantiates a slot for child data in {@link VfsData} in-memory cache
   * Note: the id is _not_ added to a parent's children list -- this should be done separately.
   */
  //@GuardedBy("directoryData")
  private @NotNull VirtualFileSystemEntry createChildImpl(int id,
                                                          int nameId,
                                                          @PersistentFS.Attributes int attributes,
                                                          boolean isEmptyDirectory) {
    FileLoadingTracker.fileLoaded(this, nameId);

    VfsData vfsData = getVfsData();
    VfsData.Segment segment = vfsData.getSegment(id, true);

    boolean isDirectory = PersistentFS.isDirectory(attributes);
    Object fileData = isDirectory ? new VfsData.DirectoryData() : KeyFMap.EMPTY_MAP;
    segment.initFileData(id, fileData, this);

    VirtualFileSystemEntry child = vfsData.getFileById(id, this, /*putIntoCache: */ true);
    assert child != null;

    segment.setFlags(id, ALL_FLAGS_MASK, VfsDataFlags.toFlags(attributes, isDirectory));
    child.updateLinkStatus(this);

    if (fileSystem.markNewFilesAsDirty()) {
      child.markDirty();
    }
    if (isDirectory && (child instanceof VirtualDirectoryImpl) && isEmptyDirectory) {
      // When creating an empty directory, we need to make sure that every file created inside it will fire a "file created" event
      // for virtual file pointer manager to update its pointers properly
      // (because currently VirtualFilePointerManager ignores empty directory creation events for performance reasons).
      VfsData.DirectoryData childVfsData = ((VirtualDirectoryImpl)child).directoryData;
      synchronized (childVfsData) {
        childVfsData.children = childVfsData.children.withAllChildrenLoaded(true);
      }
    }

    return child;
  }

  private @Nullable VirtualFileSystemEntry createAndFindChildWithEventFire(@NotNull String name) {
    VirtualFile fake = new FakeVirtualFile(this, name);
    FileAttributes attributes = fileSystem.getAttributes(fake);
    if (attributes == null) return null;
    String realName = fileSystem.getCanonicallyCasedName(fake);
    boolean isDirectory = attributes.isDirectory();
    boolean isEmptyDirectory = isDirectory && !fileSystem.hasChildren(fake);
    String symlinkTarget = attributes.isSymLink() ? fileSystem.resolveSymLink(fake) : null;
    ChildInfo[] children = isEmptyDirectory ? ChildInfo.EMPTY_ARRAY : null;
    var event = new VFileCreateEvent(REFRESH_REQUESTOR, this, realName, isDirectory, attributes, symlinkTarget, children);
    RefreshQueue.getInstance().processEvents(false, List.of(event));
    return findChild(realName);
  }

  private void updateCaseSensitivityIfUnknown(@NotNull String childName) {
    PersistentFSImpl pFS = owningPersistentFS();
    VFilePropertyChangeEvent caseSensitivityEvent = pFS.determineCaseSensitivityAndPrepareUpdate(this, childName);
    if (caseSensitivityEvent != null) {
      //TODO RC: here we immediately apply the new case-sensitivity to the this-dir.
      //         And inside determineCaseSensitivityAndPrepareUpdate() we also immediately apply the new value, if it does not
      //         lead to externally-visible change (ie. if it is == default).
      //         But in other uses of determineCaseSensitivityAndPrepareUpdate() we do NOT do that -- we do not immediately apply
      //         new cs-value, but just post the cs-changing event. Why we difference?
      //         Could some changes be lost because of this?
      pFS.executeChangeCaseSensitivity(this, (CaseSensitivity)caseSensitivityEvent.getNewValue());
      // fire event asynchronously to avoid deadlocks with possibly currently held VFP/Refresh queue locks
      RefreshQueue.getInstance().processEvents(/*async: */ true, List.of(caseSensitivityEvent));
    }
    else if (getChildrenCaseSensitivity().isUnknown()) {
      // Fallback: cache 'default' case sensitivity when we failed to read it from the disk, to avoid freezes on
      // constant attempts to re-read -- but do not save the new value in persistence:
      boolean defaultCaseSensitivity = fileSystem.isCaseSensitive();
      setCaseSensitivityFlag(defaultCaseSensitivity);
    }
  }

  private void resortChildren() {
    //re-sorts children forcibly, regardless of current children.isSorted value:
    synchronized (directoryData) {
      boolean isCaseSensitive = isCaseSensitive();
      VfsData.ChildrenIds sortedChildren = sortChildrenByName(directoryData.children, isCaseSensitive);
      assertConsistency(isCaseSensitive, sortedChildren, "afterCaseSensitivityChanged", isCaseSensitive);
    }
  }

  private @NotNull VfsData.ChildrenIds sortChildrenByName(@NotNull VfsData.ChildrenIds children,
                                                          boolean isCaseSensitive) {
    VfsData vfsData = getVfsData();
    synchronized (directoryData) {
      Comparator<VirtualFile> byName = (f1, f2) -> compareNames(f1.getName(), f2.getName(), isCaseSensitive);
      VfsData.ChildrenIds sortedChildren = children.sorted(
        id -> vfsData.getFileById(id, this, /*putIntoMemory: */true),
        byName
      );
      directoryData.children = sortedChildren;
      return sortedChildren;
    }
  }

  @Override
  public @Nullable NewVirtualFile refreshAndFindChild(@NotNull String name) {
    return findChild(name, /*doRefresh: */ true, /*canonicalizeName: */ true);
  }

  @Override
  public @Nullable NewVirtualFile findChildIfCached(@NotNull String name) {
    VirtualFileSystemEntry found = findInCachedChildren(name, isCaseSensitive());
    //noinspection UseVirtualFileEquals
    return found == NULL_VIRTUAL_FILE ? null : found;
  }

  @Override
  public @NotNull @Unmodifiable Iterable<VirtualFile> iterInDbChildren() {
    PersistentFSImpl pFS = owningPersistentFS();
    if (!pFS.wereChildrenAccessed(this)) {
      return Collections.emptyList();
    }

    if (pFS.areChildrenLoaded(this)) {
      return Arrays.asList(getChildren()); // may load VFS from other projects
    }

    loadPersistedChildren();

    return getCachedChildren();
  }

  @Override
  public @NotNull @Unmodifiable Iterable<VirtualFile> iterInDbChildrenWithoutLoadingVfsFromOtherProjects() {
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
    for (String name : names) {
      findChild(name, /*doRefresh: */ false, /*canonicalizeName: */ false);
    }
  }

  private VirtualFile @NotNull [] loadAllChildren(boolean sortChildrenOnLoading) {
    VfsData vfsData = getVfsData();
    PersistentFSImpl pFS = vfsData.owningPersistentFS();

    List<? extends ChildInfo> childrenInfo = pFS.listAll(this);

    boolean reallyNeedsSorting = sortChildrenOnLoading && (childrenInfo.size() > 1);
    if (reallyNeedsSorting) {
      String someChildName = childrenInfo.get(0).getName().toString();
      updateCaseSensitivityIfUnknown(someChildName);
    }
    boolean isCaseSensitive = isCaseSensitive();

    synchronized (directoryData) {
      if (childrenInfo.isEmpty()) {
        directoryData.clearAdoptedNames();
        directoryData.children = VfsData.ChildrenIds.EMPTY.withAllChildrenLoaded(true);
        return VirtualFile.EMPTY_ARRAY;
      }

      //We could load children unsorted, and delay the sorting until someone really asks for it:
      if (reallyNeedsSorting) {
        IntRef errorCount = new IntRef(0);
        List<? extends ChildInfo> _childrenInfo = childrenInfo;//effectively-final, for capturing in lambda
        childrenInfo = ContainerUtil.sorted(childrenInfo, (info1, info2) -> {
          CharSequence name1 = info1.getName();
          CharSequence name2 = info2.getName();
          int cmp = compareNames(name1, name2, isCaseSensitive);
          //TODO RC: the branch below is different from all other places there we sort files by name: here is the only place
          //         there we switch to case-sensitive sorting to make exact ordering -- in all other places we stop on the
          //         first step above. Why the difference? Could it bring us any issues?
          if (cmp == 0 && name1 != name2) {
            if (errorCount.get() == 0) {
              boolean wasChildrenLoaded = pFS.areChildrenLoaded(this);
              THROTTLED_LOG.error(owningPersistentFS() + " returned duplicate file names('" + name1 + "', '" + name2 + "')" +
                                  " caseSensitive: " + isCaseSensitive +
                                  " SystemInfo.isFileSystemCaseSensitive: " + SystemInfo.isFileSystemCaseSensitive +
                                  " SystemInfo.OS: " + SystemInfo.OS_NAME + " " + SystemInfo.OS_VERSION +
                                  " wasChildrenLoaded: " + wasChildrenLoaded +
                                  " in the dir: " + this + "; " + _childrenInfo.size() +
                                  " children: " + StringUtil.first(_childrenInfo.toString(), 300, true));
            }
            errorCount.inc();
            if (!isCaseSensitive) {
              // Sometimes file system rules for case-insensitive comparison differ from Java rules.
              // E.g., on NTFS files named 'ẛ' (small long S with dot) and 'Ṡ' (capital S with dot) can coexist
              // despite the uppercase for 'ẛ' being 'Ṡ' - probably because the lower case of 'Ṡ' is 'ṡ' (small S with dot), not 'ẛ'.
              // When encountering such a case, we fall back to case-sensitive comparison to establish some order between these names.
              cmp = compareNames(name1, name2, /*caseSensitive: */ true);
            }
          }
          return cmp;
        });
      }

      IntSet prevChildren = directoryData.children.toIntSet();
      VirtualFile[] files = new VirtualFile[childrenInfo.size()];
      int[] newChildrenIds = new int[files.length];
      for (int i = 0; i < files.length; i++) {
        ChildInfo child = childrenInfo.get(i);
        int childId = child.getId();
        newChildrenIds[i] = childId;
        prevChildren.remove(childId);
        VirtualFileSystemEntry file = vfsData.getFileById(childId, this, /*putIntoMemory: */true);
        if (file == null) {
          int attributes = pFS.getFileAttributes(childId);
          boolean isEmptyDirectory = PersistentFS.isDirectory(attributes) && !pFS.mayHaveChildren(childId);
          file = createChildImpl(childId, child.getNameId(), attributes, isEmptyDirectory);
        }
        files[i] = file;
      }
      if (!prevChildren.isEmpty()) {
        var missing = vfsData.getFileById(prevChildren.iterator().nextInt(), this, /*putInCache: */false);
        LOG.error("Loaded child disappeared: parent=" + verboseToString(this) + "; child=" + verboseToString(missing));
      }

      directoryData.clearAdoptedNames();
      directoryData.children = new VfsData.ChildrenIds(newChildrenIds, sortChildrenOnLoading, /*allChildren: */ true);
      if (CHECK_CONSISTENCY) {
        assertConsistency(isCaseSensitive, childrenInfo);
      }

      return files;
    }
  }

  @Override
  public VirtualFile @NotNull [] getChildren() {
    return getChildren(/*requireSorting */ true);
  }

  @Override
  @ApiStatus.Internal
  public VirtualFile @NotNull [] getChildren(boolean requireSorting) {
    if (!isValid()) {
      return handleInvalidDirectory(EMPTY_ARRAY);
    }
    if (allChildrenLoaded()) {
      if (requireSorting && !directoryData.children.isSorted()) {
        //MAYBE RC: check case-sensitivity is defined? updateCaseSensitivityIfUnknown()?
        ensureChildrenSorted(isCaseSensitive());
      }
      return cachedChildrenArray( /*putToMemoryCache: */ true);
    }
    return loadAllChildren(requireSorting);
  }

  @Override
  public @Nullable VirtualFileSystemEntry findChild(@NotNull String name) {
    return findChild(name, /*doRefresh: */ false, /*canonicalizeName: */ true);
  }

  /**
   * @return file with childId, _if it belongs to this directory's children_, null otherwise.
   * If the file is in VFS, but not in memory cache -- loads it in memory, adds file.id into .children list
   */
  @ApiStatus.Internal
  public @Nullable VirtualFileSystemEntry findChildById(int childId) {
    if (childId == this.getId()) {
      //we could just return null (='no such child'), but such a call 99% is a bug:
      throw new IllegalArgumentException("Trying to find childId(=" + childId + ") in parent(=" + getId() + ")");
    }

    //We come here _only_ from PersistentFSImpl.findFileById(), on a descend phase, where we resolve fileIds to
    // VFiles. Hence, it _must_ be a child with childId -- because 'this' was collected as .parent during an
    // ascend phase. If that is not the case -- either something was changed in between (e.g., children were
    // refreshed), or there is an inconsistency in VFS (e.g., children and .parent fell out of sync somehow):

    VfsData vfsData = getVfsData();
    PersistentFSImpl pFS = vfsData.owningPersistentFS();

    VirtualFileSystemEntry child = vfsData.getFileById(childId, this, /*putToCache: */ true);
    if (child != null && !child.isValid()) {
      return null; // =removed
    }

    synchronized (directoryData) {
      if (child == null) {
        //childId hasn't been loaded from persistence yet: load it
        @PersistentFS.Attributes int childAttributes = pFS.getFileAttributes(childId);
        if (PersistentFSRecordAccessor.hasDeletedFlag(childAttributes)) {
          return null;
        }
        FSRecordsImpl vfsPeer = pFS.peer();
        int childNameId = vfsPeer.getNameIdByFileId(childId);
        boolean isEmptyDirectory = PersistentFS.isDirectory(childAttributes) && !pFS.mayHaveChildren(childId);
        child = createChildIfNotExist(childId, childNameId, childAttributes, isEmptyDirectory);
      }

      //now check child is indeed a child of this dir:
      VfsData.ChildrenIds children = directoryData.children;

      //MAYBE RC: code below is similar to addChild(child) -- how to reduce code duplication?

      boolean allChildrenLoaded = children.areAllChildrenLoaded();
      if (children.isSorted() && worthBinarySearch(children)) {
        String childName = child.getName();
        //If children are sorted => 99% caseSensitivity _is_ known; otherwise how could children be sorted?
        // The only exception is children.size=1, but this is rejected by .worthBinarySearch(). But lets be on a safe side:
        updateCaseSensitivityIfUnknown(childName);
        boolean isCaseSensitive = isCaseSensitive();

        int indexOfName = findIndexByName(children, childName, isCaseSensitive);
        if (indexOfName >= 0) {
          return child;//childId is in cached children: OK
        }
        else if (!allChildrenLoaded
                 && (TRUST_FIND_CHILD_BY_ID_CALLERS || isInPersistentChildren(pFS, getId(), childId))) {
          // Assume (either by 'trust' or because we checked) that childId is indeed a child of this directory
          // => we add it to the children list, once it is not there yet:
          int insertionIndex = -indexOfName - 1;
          directoryData.children = children.insertAt(insertionIndex, childId);
          return child;
        }
      }
      else {
        int indexOfId = children.indexOfId(childId);
        if (indexOfId >= 0) {
          return child;//childId is in cached children: OK
        }
        else if (!allChildrenLoaded
                 && (TRUST_FIND_CHILD_BY_ID_CALLERS || isInPersistentChildren(pFS, getId(), childId))) {
          // Assume (either by 'trust' or because we checked) that childId is indeed a child of this directory
          // => we add it to the children list, once it is not there yet:
          //Here we 'trust' that childId is indeed a child of this directory -- so we add it to the children list, if it
          // is not there yet -- we do not check childId really belongs to the children list
          directoryData.children = children.appendId(childId);
          return child;
        }
      }

      //childId not really belong to children:
      LOG.error(child + " expected to be in [" + this + "].children=" + children + ", but absent -> refresh race or VFS inconsistency?");
      return null;
    }
    //since the child is guaranteed to be in children _already known to VFS_ -- adoptedNames shouldn't be changed at all
  }

  /**
   * @return a _cached_ file by childId, if it belongs to this directory's _cached_ in-memory children,
   * null otherwise (method name may be a bit misleading)
   */
  private @Nullable VirtualFileSystemEntry findCachedChildById(int childId) {
    int indexOfChild = directoryData.children.indexOfId(childId);
    if (indexOfChild >= 0) {
      VirtualFileSystemEntry fileById = getVfsData().getFileById(childId, this, /*putToCache: */true);
      if (fileById != null) {
        if (fileById.getId() == childId) {
          return fileById;
        }

        //MAYBE RC: how could it be? Maybe throw an exception would be better reaction?
        LOG.error("getFileById(" + childId + ") returns " + fileById + " with different id(=" + fileById.getId() + ") -> return null");
      }
    }
    return null;
  }

  // optimization: works faster than added.forEach(this::addChild)
  @ApiStatus.Internal
  @Contract(mutates = "this,param1")
  public void createAndAddChildren(@NotNull List<ChildInfo> added,
                                   boolean markAllChildrenLoaded,
                                   @NotNull BiConsumer<? super VirtualFile, ? super ChildInfo> callback) {
    int addedSize = added.size();
    if (addedSize <= 1) {//fast-path:
      synchronized (directoryData) {
        for (int i = 0; i < addedSize; i++) {
          ChildInfo info = added.get(i);
          assert info.getId() > 0 : info;
          @PersistentFS.Attributes int attributes = info.getFileAttributeFlags();
          boolean isEmptyDirectory = (info.getChildren() != null) && (info.getChildren().length == 0);

          //We look for existing child in children by-id because, likely, linear O(N) search in int[] is still faster than
          // O(logN) binary search in a sorted array, but with much more expensive (by-file-name)comparator. In the second
          // branch below we use binary-search, probably, because for joining 2 lists (as opposite of inserting single item)
          // this would be faster
          int indexOfId = directoryData.children.indexOfId(info.getId());
          if (indexOfId < 0) {
            VirtualFileSystemEntry file = createChildImpl(info.getId(), info.getNameId(), attributes, isEmptyDirectory);
            addChild(file);//directoryData.children is re-assigned inside .addChild()
            callback.accept(file, info);
          }//else: child is already present in children
        }
        if (markAllChildrenLoaded) {
          directoryData.children = directoryData.children.withAllChildrenLoaded(true);
        }
        return;
      }
    }

    // slow-path: when there are many children, it's cheaper to merge sorted lists just like in merge sort
    //MAYBE RC: ensure case-sensitivity is known? updateCaseSensitivityIfUnknown(childName);
    boolean isCaseSensitive = isCaseSensitive();
    Comparator<ChildInfo> byName = (p1, p2) -> compareNames(p1.getName(), p2.getName(), isCaseSensitive);
    added.sort(byName);

    FSRecordsImpl vfsPeer = owningPersistentFS().peer();
    synchronized (directoryData) {
      //TODO RC: if children is not sorted -- we could still work with unsorted, merge the lists by-id
      VfsData.ChildrenIds oldChildren = ensureChildrenSorted(isCaseSensitive);
      IntList mergedIds = new IntArrayList(oldChildren.size() + addedSize);

      List<ChildInfo> existingChildren = new AbstractList<>() {
        @Override
        public ChildInfo get(int index) {
          int id = oldChildren.id(index);
          assert id > 0 : id;
          int nameId = vfsPeer.getNameIdByFileId(id);
          return new ChildInfoImpl(id, nameId, null, null, null/*irrelevant here*/);
        }

        @Override
        public int size() {
          return oldChildren.size();
        }
      };
      ContainerUtil.processSortedListsInOrder(existingChildren, added, byName, /*mergeEqualItems: */ true, (nextInfo, mergeResult) -> {
        //TODO RC: this branch differs from (added.size=1) branch above in few aspects.
        //         The most noticeable are comparison: we merge existing/added lists by-name here, but we look up existing
        //         fileId above. This could be explained though, by the performance considerations.
        //         Another difference is that here we call callback.accept() first, and update directoryData.children list with
        //         the new files afterwards, but in 1-item branch above we add new file to .children list first,
        //         and call the callback afterwards -- I'm not sure that semantic consequences could be of this difference.
        if (mergeResult == ContainerUtil.MergeResult.COPIED_FROM_LIST2) {
          assert nextInfo.getId() > 0 : nextInfo;
          @PersistentFS.Attributes int attributes = nextInfo.getFileAttributeFlags();
          boolean isEmptyDirectory = nextInfo.getChildren() != null && nextInfo.getChildren().length == 0;
          directoryData.removeAdoptedName(nextInfo.getName());
          VirtualFileSystemEntry file = createChildImpl(nextInfo.getId(), nextInfo.getNameId(), attributes, isEmptyDirectory);
          callback.accept(file, nextInfo);
        }
        mergedIds.add(nextInfo.getId());
      });

      if (markAllChildrenLoaded) {
        directoryData.children = oldChildren.withIds(mergedIds.toIntArray()).withAllChildrenLoaded(true);
      }
      else {
        directoryData.children = oldChildren.withIds(mergedIds.toIntArray());
      }
      assertConsistency(isCaseSensitive, added);
    }
  }

  /** changes nothing if child.id is already in directoryData.children list */
  public void addChild(@NotNull VirtualFileSystemEntry child) {
    int childId = child.getId();
    if (childId == this.getId()) {
      throw new IllegalArgumentException("Trying to add child(=" + child + ") to itself (parent=" + this + ")");
    }

    String childName = child.getName();
    synchronized (directoryData) {
      directoryData.removeAdoptedName(childName);

      VfsData.ChildrenIds children = directoryData.children;
      if (children.isSorted() && worthBinarySearch(children)) {
        //If children are sorted => 99% caseSensitivity _is_ known; otherwise how could children be sorted?
        // The only exception is children.size=1, but this is rejected by .worthBinarySearch(). But lets be on a safe side:
        updateCaseSensitivityIfUnknown(childName);
        boolean isCaseSensitive = isCaseSensitive();

        int childIndex = findIndexByName(children, childName, isCaseSensitive);
        if (childIndex < 0) {
          int insertionIndex = -childIndex - 1;
          directoryData.children = children.insertAt(insertionIndex, childId);
          assertConsistency(isCaseSensitive, child, "insertionIndex", insertionIndex);
        }// else: child already presents in children
      }
      else {
        int childIndex = children.indexOfId(childId);
        if (childIndex < 0) {
          directoryData.children = children.appendId(childId);//'sorted' property will be lost!
          boolean isCaseSensitive = isCaseSensitive();
          assertConsistency(isCaseSensitive, child);
        }//else: child is already present in children
      }
    }
  }

  public void removeChild(@NotNull VirtualFileSystemEntry child) {
    boolean isCaseSensitive = isCaseSensitive();
    String name = child.getName();
    int childId = child.getId();
    synchronized (directoryData) {
      VfsData.ChildrenIds children = directoryData.children;

      int childIndex;
      if (children.isSorted() && worthBinarySearch(children)) {
        childIndex = findIndexByName(children, name, isCaseSensitive);
      }
      else {// otherwise: linear search
        childIndex = children.indexOfId(childId);
      }

      if (childIndex >= 0) {
        // it can be that we ask to add a name to the adopted list whereas it is already contained in the real part -
        // in this case, we should remove it from the latter
        directoryData.children = children.removeAt(childIndex);
      }

      // it can be that we ask to add a name to the adopted list whereas it is already contained in the real part -
      // in this case, we should remove it from the latter
      if (!allChildrenLoaded()) {//RC: I don't get why we add just-removed name to adoptedNames?
        directoryData.addAdoptedName(name, isCaseSensitive);
      }

      assertConsistency(isCaseSensitive, child);
    }
  }

  // optimization: faster than forEach(this::removeChild)
  public void removeChildren(@NotNull IntSet idsToRemove, @NotNull List<? extends CharSequence> namesToRemove) {
    boolean isCaseSensitive = isCaseSensitive();
    synchronized (directoryData) {
      directoryData.children = directoryData.children.removeIds(idsToRemove);

      if (!allChildrenLoaded()) {
        directoryData.addAdoptedNames(namesToRemove, isCaseSensitive);
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
    VfsData vfsData = getVfsData();
    FSRecordsImpl peer = vfsData.owningPersistentFS().peer();
    Set<CharSequence> existingNames = CollectionFactory.createCharSequenceSet(isCaseSensitive, directoryData.children.size());
    synchronized (directoryData) {
      VfsData.ChildrenIds children = directoryData.children;
      for (int i = 0; i < children.size(); i++) {
        int id = children.id(i);
        existingNames.add(vfsData.getNameByFileId(id));
      }
      int parentId = getId();
      existingNames.addAll(peer.listNames(parentId));

      validateAgainst(childrenToCreate, existingNames);

      if (!childrenToCreate.isEmpty() && !allChildrenLoaded()) {
        // findChild asks FS when failed to locate a child, and so should we
        int beforeSize = existingNames.size();
        String[] names = fileSystem.list(this);
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
      return !directoryData.isAdoptedName(childName) && existingNames.contains(childName);
    });
  }

  public boolean allChildrenLoaded() {
    return directoryData.children.areAllChildrenLoaded();
  }

  public @Unmodifiable @NotNull List<String> getSuspiciousNames() {
    return directoryData.getAdoptedNames();
  }

  /**
   * @return index of a child with the name given (taking into account case sensitivity given), among children,
   * or (-insertionIndex-1), if there is no child with the name given.
   * The .children must be sorted, or exception will be thrown!
   */
  private int findIndexByName(@NotNull VfsData.ChildrenIds children,
                              @NotNull CharSequence name,
                              boolean isCaseSensitive) {
    if (!children.isSorted()) {
      throw new IllegalStateException("children is not sorted -- can't find index by name");
    }
    FSRecordsImpl peer = owningPersistentFS().peer();
    return children.findIndexByName(
      name,
      (name1, name2) -> compareNames(name1, name2, isCaseSensitive),
      id -> peer.getName(id)
    );
  }

  @Override
  public @NotNull @Unmodifiable List<VirtualFile> getCachedChildren() {
    return Arrays.asList(cachedChildrenArray(/*putToCache: */false));
  }

  @Override
  public byte @NotNull [] contentsToByteArray() throws IOException {
    throw new IOException("Cannot get content of directory: " + this);
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
    for (VirtualFileSystemEntry child : cachedChildrenArray(/*putToCache: */true)) {
      child.markDirtyInternal();
      if (child instanceof VirtualDirectoryImpl) {
        ((VirtualDirectoryImpl)child).markDirtyRecursivelyInternal();
      }
    }
  }

  @Override
  protected void setUserMap(@NotNull KeyFMap map) {
    directoryData.userMap = map;
  }

  @ApiStatus.Internal
  @Override
  public @NotNull KeyFMap getUserMap() {
    return directoryData.userMap;
  }

  @Override
  protected boolean changeUserMap(@NotNull KeyFMap oldMap, @NotNull KeyFMap newMap) {
    checkLeaks(newMap);
    return directoryData.changeUserMap(oldMap, UserDataInterner.internUserData(newMap));
  }

  @Override
  public boolean isCaseSensitive() {
    return isChildrenCaseSensitivityKnown() ? getFlagInt(VfsDataFlags.CHILDREN_CASE_SENSITIVE) : super.isCaseSensitive();
  }

  /**
   * For NTFS, APFS (MacOS) there is a default case-sensitivity for a file-system (they are case-insensitive), but that
   * default could be overridden per-directory (NTFS) or per partition (APFS).
   * Hence, even while we do know default CS for the file-system, we don't really know a case-sensitivity of a current
   * folder, until we explicitly query it. This method provides info about do we already query and cache the actual
   * case-sensitivity of this folder, or not.
   *
   * @return is this folder actual case-sensitivity was determined and cached?
   */
  @ApiStatus.Internal
  private boolean isChildrenCaseSensitivityKnown() {
    return getFlagInt(VfsDataFlags.CHILDREN_CASE_SENSITIVITY_CACHED);
  }

  /**
   * @return the value of CHILDREN_CASE_SENSITIVE bit, if CHILDREN_CASE_SENSITIVITY_CACHED bit is set, or UNKNOWN otherwise
   */
  @ApiStatus.Internal
  public @NotNull CaseSensitivity getChildrenCaseSensitivity() {
    return isChildrenCaseSensitivityKnown() ?
           CaseSensitivity.fromBoolean(isCaseSensitive()) : CaseSensitivity.UNKNOWN;
  }

  private VirtualFileSystemEntry @NotNull [] cachedChildrenArray(boolean putToMemoryCache) {
    VfsData vfsData = getVfsData();
    return directoryData.children.asFiles(fileId -> vfsData.getFileById(fileId, this, putToMemoryCache));
  }

  /** @return true if childId is in a persistent (=not cached in memory!) children list of parentId */
  private static boolean isInPersistentChildren(@NotNull PersistentFSImpl pFS,
                                                int parentId,
                                                int childId) {
    //MAYBE RC: better to check childId is in children _without_ loading _all_ the children (which could be quite large)
    //          Maybe create FSRecordsImpl.isInChildren(parentId, childId) method?
    int[] childrenFromPersistence = pFS.peer().listIds(parentId);
    for (int id : childrenFromPersistence) {
      if (id == childId) {
        return true;
      }
    }
    return false;
  }

  // =============================== helpers ====================================================================================== //

  /** removes forward/backslashes from start/end and return trimmed name or null if there are slashes in the middle, or it's empty */
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

  private static boolean worthBinarySearch(VfsData.ChildrenIds children) {
    return children.size() > LINEAR_SEARCH_THRESHOLD;
  }

  private void logChildLookupFailure(@NotNull PersistentFSImpl pfs,
                                     int childId,
                                     int childNameId,
                                     @NotNull String childName) {
    FSRecordsImpl vfsPeer = pfs.peer();
    LOG.warn(
      "Child[#" + childId + ", nameId: " + childNameId + "][name='" + vfsPeer.getNameByNameId(childNameId) + "']" +
      " present in a children list [" + directoryData.children + "], " +
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

  private void error(String message, Object... details) {
    var builder = new StringBuilder().append(message).append("\n--- children ---");
    for (var child : cachedChildrenArray(true)) builder.append('\n').append(verboseToString(child));
    builder.append("--- details ---");
    for (var o : details) builder.append('\n').append(o instanceof Object[] ? Arrays.toString((Object[])o) : o.toString());
    throw new AssertionError(builder.toString());
  }

  private void assertConsistency(boolean isCaseSensitive, Object @NotNull ... details) {
    if (!CHECK_CONSISTENCY || ApplicationManagerEx.isInStressTest()) return;

    VfsData.ChildrenIds children = directoryData.children;
    if (children.size() == 0) {
      return;
    }
    boolean sorted = children.isSorted();
    VfsData vfsData = getVfsData();
    CharSequence prevName = vfsData.getNameByFileId(children.id(0));
    for (int i = 1; i < children.size(); i++) {
      int id = children.id(i);
      CharSequence name = vfsData.getNameByFileId(id);
      if (sorted) {
        int prev = children.id(i - 1);
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
      }
      synchronized (directoryData) {
        if (directoryData.isAdoptedName(name)) {
          List<String> adoptedNames = directoryData.getAdoptedNames();
          directoryData.removeAdoptedName(name);
          String message = "In " + verboseToString(this) + " file '" + name + "' is both `child` and `adopted`";
          error(message, "Adopted: " + adoptedNames + ";\n ", details);
        }
      }
    }
  }

  private static String verboseToString(@Nullable VirtualFileSystemEntry file) {
    return file == null ? "null" :
           file +
           " (name: '" + file.getName() + "', " + file.getClass() +
           ", parent: " + file.getParent() +
           ", id: " + file.getId() +
           ", FS: " + file.getFileSystem() +
           ", fs.attrs: " + file.getFileSystem().getAttributes(file) +
           ", isCaseSensitive: " + file.isCaseSensitive() +
           ", canonical: " + file.getFileSystem().getCanonicallyCasedName(file) +
           ')';
  }

  protected static void checkLeaks(@NotNull KeyFMap newMap) {
    for (Key<?> key : newMap.getKeys()) {
      if (newMap.get(key) instanceof PsiCachedValue) {
        throw new AssertionError("Don't store CachedValue in VFS user data, since it leads to memory leaks");
      }
    }
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

  protected static <T, E extends Throwable> T runUnderAllLocksAcquired(@NotNull ThrowableComputable<T, E> lambda,
                                                                       @NotNull VirtualDirectoryImpl dir1,
                                                                       @NotNull VirtualDirectoryImpl dir2) throws E {
    Object lock1 = dir1.directoryData;
    Object lock2 = dir2.directoryData;
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
}
