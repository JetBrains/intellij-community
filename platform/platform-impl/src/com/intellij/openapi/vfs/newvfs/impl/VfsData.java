// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.impl;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationListener;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.InvalidVirtualFileAccessException;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecordsImpl;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl;
import com.intellij.util.*;
import com.intellij.util.concurrency.AtomicFieldUpdater;
import com.intellij.util.containers.*;
import com.intellij.util.keyFMap.KeyFMap;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.IntFunction;

/**
 * Main part of VFS in-memory cache: flags, user data and children are all stored here.
 * {@link VirtualFileSystemEntry} and {@link VirtualDirectoryImpl} objects mainly just store fileId, so they could be
 * seen as 'pointers' into this cache.
 * {@link com.intellij.openapi.vfs.newvfs.persistent.VirtualDirectoryCache} is another part of VFS in-memory cache, which caches
 * {@link VirtualDirectoryImpl} objects.
 * <p>
 * The purpose is to avoid holding this data in separate immortal file/directory objects because that involves space overhead, significant
 * when there are hundreds of thousands of files.
 * <p>
 * The data is stored per-id in blocks of {@link #SEGMENT_SIZE}. File ids in one project tend to cluster together,
 * so the overhead for non-loaded id should not be large in most cases.
 * <p>
 * File objects are still created if needed. There might be several objects for the same file, so equals() should be used instead of ==.
 * <p>
 * The lifecycle of a file object is as follows:
 *
 * <ol>
 * <li> The file has not been instantiated yet, so {@link #getFileById} returns null. </li>
 *
 * <li> A file is explicitly requested by calling getChildren or findChild on its parent. The parent initializes all the necessary
 * data (in a thread-safe context) and creates the file instance.
 * See {@link Segment#initFileData(int, Object, VirtualDirectoryImpl)} </li>
 *
 * <li> After that the file is live, an object representing it can be retrieved any time from its parent. File system roots are
 * kept on hard references in {@link PersistentFS} </li>
 *
 * <li> If a file is deleted (invalidated), then its data is not needed anymore, and should be removed. But this can only happen
 * after all the listener has been notified about the file deletion and have had their chance to look at the data the last time.
 * See {@link #killInvalidatedFiles()} </li>
 *
 * <li> The file with removed data is marked as "dead" (see {@link #deadMarker}), any access to it will throw {@link InvalidVirtualFileAccessException}
 * Dead ids won't be reused in the same session of the IDE. </li>
 * </ol>
 */
@ApiStatus.Internal
public final class VfsData {
  private static final Logger LOG = Logger.getInstance(VfsData.class);

  private static final int SEGMENT_BITS = 9;
  private static final int SEGMENT_SIZE = 1 << SEGMENT_BITS;
  private static final int OFFSET_MASK = SEGMENT_SIZE - 1;

  private final Application app;
  private final PersistentFSImpl owningPersistentFS;

  private final Object deadMarker = ObjectUtils.sentinel("dead file");

  //TODO RC: seems like the segments are only cached, but never evicted -- this could create memory problems
  //TODO RC: FSRecords was quite optimized recently, probably caching is not needed anymore?
  //         indexingFlag/nameId caching was already removed -- need to think through about remaining (flag+modCount)
  //         field: on the first sight they look like an additional data, independent from persistent VFS data?
  //         .children is another thing that could be less cached -- in many cases children could be accessed directly from
  //         FSRecords?

  /** [segmentIndex -> Segment] */
  private final ConcurrentIntObjectMap<Segment> segments = ConcurrentCollectionFactory.createConcurrentIntObjectMap();

  /**
   * Set of deleted file ids. Never cleaned during a session (deleted fileId could be re-used,
   * but it could be done only on a session start, see {@link FSRecordsImpl})
   */
  private final ConcurrentBitSet invalidatedFileIds = ConcurrentBitSet.create();

  /**
   * Records (fileIds) to be invalidated: removed from {@link #changedParents} and set 'dead' in {@link Segment#objectFieldsArray}).
   * As soon, as apt slots are processed, this queue is cleared.
   * Guarded by {@link #deadMarker}
   */
  private IntSet queueOfFileIdsToBeInvalidated = new IntOpenHashSet();

  private final IntObjectMap<VirtualDirectoryImpl> changedParents = ConcurrentCollectionFactory.createConcurrentIntObjectMap();

  public VfsData(@NotNull Application app,
                 @NotNull PersistentFSImpl pfs) {
    this.app = app;
    this.owningPersistentFS = pfs;
    app.addApplicationListener(new ApplicationListener() {
      @Override
      public void writeActionFinished(@NotNull Object action) {
        // after top-level write action is finished, all the deletion listeners should have processed the deleted files
        // and their data is considered safe to remove. From this point on accessing a removed file will result in an exception.
        if (!app.isWriteAccessAllowed()) {
          killInvalidatedFiles();
        }
      }
    }, app);
  }

  @NotNull PersistentFSImpl owningPersistentFS() {
    return owningPersistentFS;
  }

  private void killInvalidatedFiles() {
    synchronized (deadMarker) {
      if (!queueOfFileIdsToBeInvalidated.isEmpty()) {
        for (IntIterator iterator = queueOfFileIdsToBeInvalidated.iterator(); iterator.hasNext(); ) {
          int id = iterator.nextInt();
          Segment segment = Objects.requireNonNull(getSegment(id, false));
          segment.objectFieldsArray.set(objectOffsetInSegment(id), deadMarker);
          //skip cleaning segment.intFieldsArray since dead marker means slot is _not reusable_ in this session
          changedParents.remove(id);
        }
        queueOfFileIdsToBeInvalidated = new IntOpenHashSet();
      }
    }
  }

  /**
   * @return a VirtualFileSystemEntry wrapper for the file data in the cache ({@link #segments}).
   * If there is no data in {@link #segments} cache for given id yet -- returns null.
   * If the file with given id was deleted -- throws {@link InvalidVirtualFileAccessException}.
   * <p/>
   * If putToMemoryCache=true, and the wrapper created is a directory -- it is also put into {@link PersistentFSImpl#dirByIdCache}.
   * If the given id corresponds to a file, not a directory -- this param has no effect.
   */
  @Nullable VirtualFileSystemEntry getFileById(int id, @NotNull VirtualDirectoryImpl parent, boolean putToMemoryCache) {
    VirtualFileSystemEntry dir = owningPersistentFS.getCachedDir(id);
    if (dir != null) return dir;

    Segment segment = getSegment(id, /*create: */ false);
    if (segment == null) return null;

    int offset = objectOffsetInSegment(id);
    Object entryData = segment.objectFieldsArray.get(offset);
    if (entryData == null) return null;

    if (entryData == deadMarker) {
      throw reportDeadFileAccess(new VirtualFileImpl(id, segment, parent));
    }

    if (entryData instanceof DirectoryData directoryData) {
      if (putToMemoryCache) {
        return owningPersistentFS.getOrCacheDir(new VirtualDirectoryImpl(id, segment, directoryData, parent, parent.getFileSystem()));
      }
      else {
        VirtualFileSystemEntry entry = owningPersistentFS.getCachedDir(id);
        if (entry != null) return entry;
        return new VirtualDirectoryImpl(id, segment, directoryData, parent, parent.getFileSystem());
      }
    }
    return new VirtualFileImpl(id, segment, parent);
  }

  private static @NotNull InvalidVirtualFileAccessException reportDeadFileAccess(@NotNull VirtualFileSystemEntry file) {
    return new InvalidVirtualFileAccessException("Accessing dead virtual file: " + file.getUrl());
  }

  @Contract("_,true->!null")
  Segment getSegment(int id, boolean create) {
    int segmentIndex = segmentIndex(id);
    Segment segment = segments.get(segmentIndex);
    if (segment != null || !create) {
      return segment;
    }
    return segments.cacheOrGet(segmentIndex, new Segment(this));
  }

  public boolean hasLoadedFile(int id) {
    Segment segment = getSegment(id, false);
    return segment != null && segment.objectFieldsArray.get(objectOffsetInSegment(id)) != null;
  }

  @NotNull String getNameByFileId(int fileId) {
    return owningPersistentFS.peer().getName(fileId);
  }

  boolean isFileValid(int id) {
    return !invalidatedFileIds.get(id);
  }

  @Nullable VirtualDirectoryImpl getChangedParent(int id) {
    return changedParents.get(id);
  }

  private void changeParent(int id, @NotNull VirtualDirectoryImpl parent) {
    app.assertWriteAccessAllowed();
    changedParents.put(id, parent);
  }

  void invalidateFile(int id) {
    invalidatedFileIds.set(id);
    synchronized (deadMarker) {
      queueOfFileIdsToBeInvalidated.add(id);
    }
  }

  /** @return offset of fileId's data in {@link Segment#objectFieldsArray} */
  private static int objectOffsetInSegment(int fileId) {
    if (fileId <= 0) throw new IllegalArgumentException("invalid argument id: " + fileId);
    return fileId & OFFSET_MASK;
  }

  private static int segmentIndex(int fileId) {
    return fileId >>> SEGMENT_BITS;
  }

  /** Caches info about SEGMENT_SIZE consequent files, indexed by fileId */
  @ApiStatus.Internal
  public static final class Segment {
    private static final int INT_FIELDS_COUNT = 1;
    private static final int FLAGS_AND_MOD_COUNT_FIELD_NO = 0;

    final @NotNull VfsData owningVfsData;

    /** user data (KeyFMap) for files, {@link DirectoryData} for folders */
    private final AtomicReferenceArray<Object> objectFieldsArray;

    /**
     * [flags | modCount] per fileId
     * Currently it is single int32 per fileId: flag bits (highest byte) and modificationCounter (lowest 3 bytes)
     * Flags are from {@link VirtualFileSystemEntry.VfsDataFlags}: they are a subset of underlying {@link PersistentFS.Flags},
     * see {@link VirtualDirectoryImpl#createChildImpl(int, int, int, boolean)} for an assignment.
     * Modification counter is separated from underlying {@link FSRecordsImpl#getModCount(int)}: it is transient (not persistent),
     * and incremented only on file _content_ modification, while {@link FSRecordsImpl#getModCount(int)} is incremented on _any_
     * file attribute/content/etc change.
     */
    private final AtomicIntegerArray intFieldsArray;

    /** the reference is synchronized by read-write lock; clients outside read-action deserve to get outdated result */
    @Nullable Segment replacement;

    @VisibleForTesting
    public Segment(@NotNull VfsData owningVfsData) {
      this(owningVfsData, new AtomicReferenceArray<>(SEGMENT_SIZE), new AtomicIntegerArray(SEGMENT_SIZE * INT_FIELDS_COUNT));
    }

    private Segment(@NotNull VfsData owningVfsData,
                    @NotNull AtomicReferenceArray<Object> objectFieldsArray,
                    @NotNull AtomicIntegerArray intFieldsArray) {
      this.objectFieldsArray = objectFieldsArray;
      this.intFieldsArray = intFieldsArray;
      this.owningVfsData = owningVfsData;
    }

    void setUserMap(int fileId, @NotNull KeyFMap map) {
      int index = objectOffsetInSegment(fileId);
      Object oldMap = objectFieldsArray.getAndSet(index, map);
      if (oldMap == null) {
        //Entry in the objectFieldsArray must be initialized first in .initFileData(), during
        // VirtualFile lookup -- only after that VirtualFile could be made publicly available,
        // and this method is available to call. This seems to be violated:
        throw new IllegalStateException("file[#" + fileId + "]: cache record wasn't initialized yet, but already used (set " + map + ")");
      }
    }

    @NotNull KeyFMap getUserMap(@NotNull VirtualFileSystemEntry file, int id) {
      Object o = objectFieldsArray.get(objectOffsetInSegment(id));
      if (!(o instanceof KeyFMap)) {
        throw reportDeadFileAccess(file);
      }
      return (KeyFMap)o;
    }

    boolean changeUserMap(int fileId, KeyFMap oldMap, KeyFMap newMap) {
      if (oldMap == null) {
        //Entry in the objectFieldsArray must be initialized first in .initFileData(), during
        // VirtualFile lookup -- only after that VirtualFile could be made publicly available,
        // and this method is available to call. This seems to be violated:
        throw new IllegalStateException(
          "file[#" + fileId + "]: cache record wasn't initialized yet, but already used (change -> " + newMap + ")");
      }
      return objectFieldsArray.compareAndSet(objectOffsetInSegment(fileId), oldMap, newMap);
    }

    boolean getFlag(int fileId, @VirtualFileSystemEntry.Flags int mask) {
      BitUtil.assertOneBitMask(mask);
      assert (mask & ~VirtualFileSystemEntry.ALL_FLAGS_MASK) == 0 : "Unexpected flag";

      int offset = fieldOffset(fileId, FLAGS_AND_MOD_COUNT_FIELD_NO);
      return (intFieldsArray.get(offset) & mask) != 0;
    }

    void setFlag(int fileId, @VirtualFileSystemEntry.Flags int mask, boolean value) {
      if (LOG.isTraceEnabled()) {
        LOG.trace("Set flag " + Integer.toHexString(mask) + "=" + value + " for id=" + fileId);
      }
      assert (mask & ~VirtualFileSystemEntry.ALL_FLAGS_MASK) == 0 : "Unexpected flag";
      int offset = fieldOffset(fileId, FLAGS_AND_MOD_COUNT_FIELD_NO);
      intFieldsArray.updateAndGet(offset, oldInt -> BitUtil.set(oldInt, mask, value));
    }

    void setFlags(int fileId, @VirtualFileSystemEntry.Flags int combinedMask, @VirtualFileSystemEntry.Flags int combinedValue) {
      if (LOG.isTraceEnabled()) {
        LOG.trace("Set flags " + Integer.toHexString(combinedMask) + "=" + combinedValue + " for id=" + fileId);
      }
      assert (combinedMask & ~VirtualFileSystemEntry.ALL_FLAGS_MASK) == 0 : "Unexpected flag";
      assert (~combinedMask & combinedValue) == 0 : "Value (" + Integer.toHexString(combinedValue) + ") set bits outside mask (" +
                                                    Integer.toHexString(combinedMask) + ")";
      int offset = fieldOffset(fileId, FLAGS_AND_MOD_COUNT_FIELD_NO);
      intFieldsArray.updateAndGet(offset, oldInt -> oldInt & ~combinedMask | combinedValue);
    }

    /**
     * Transient content modification counter: different from {@link FSRecordsImpl#getModCount(int)} -- it is not persistent,
     * and incremented only on file _content_ modification, while {@link FSRecordsImpl#getModCount(int)} is incremented on _any_
     * file attribute/content/etc change.
     */
    long getModificationStamp(int fileId) {
      int offset = fieldOffset(fileId, FLAGS_AND_MOD_COUNT_FIELD_NO);
      return intFieldsArray.get(offset) & ~VirtualFileSystemEntry.ALL_FLAGS_MASK;
    }

    void setModificationStamp(int fileId, long stamp) {
      int offset = fieldOffset(fileId, FLAGS_AND_MOD_COUNT_FIELD_NO);
      intFieldsArray.updateAndGet(offset, oldInt -> (oldInt & VirtualFileSystemEntry.ALL_FLAGS_MASK) |
                                                    ((int)stamp & ~VirtualFileSystemEntry.ALL_FLAGS_MASK));
    }

    void changeParent(int fileId, VirtualDirectoryImpl directory) {
      int segmentIndex = segmentIndex(fileId);

      assert replacement == null;
      replacement = new Segment(owningVfsData, objectFieldsArray, intFieldsArray);
      boolean replaced = owningVfsData.segments.replace(segmentIndex, this, replacement);
      assert replaced;

      owningVfsData.changeParent(fileId, directory);
    }

    //@GuardedBy("parent.directoryData")
    void initFileData(int fileId, @NotNull Object fileData, @NotNull VirtualDirectoryImpl parent) throws FileAlreadyCreatedException {
      int offset = objectOffsetInSegment(fileId);

      Object existingData = objectFieldsArray.get(offset);
      if (existingData != null) {
        //RC: it seems like concurrency issue, but I can't find a specific location
        //MAYBE RC: don't throw the exception -- if an entry was already created, so be it, log warn and go on?
        //TODO RC: why it is even an error? This could happen if the cached file entry was dropped by GC (it is a soft-ref),
        //         or sometimes just by concurrency

        FSRecordsImpl vfsPeer = owningVfsData.owningPersistentFS.peer();
        int parentId = vfsPeer.getParent(fileId);
        DirectoryData parentData;
        if (parentId == FSRecords.NULL_FILE_ID) {// => fileId is root
          parentData = null;
        }
        else {
          Segment parentSegment = owningVfsData.getSegment(parentId, false);
          parentData = (DirectoryData)parentSegment.objectFieldsArray.get(objectOffsetInSegment(parentId));
        }

        throw new FileAlreadyCreatedException(
          describeAlreadyCreatedFile(fileId)
          + " data: " + fileData
          + ", alreadyExistingData: " + existingData
          + ", parentData: " + parentData + ", parent.data: " + parent.directoryData + " equals: " + (parentData == parent.directoryData)
          + ", synchronized(parentData): " + (parentData != null ? Thread.holdsLock(parentData) : "...")
        );
      }

      objectFieldsArray.set(offset, fileData);
    }


    /** @return offset of field #fieldNo of file=fileId in a {@link #intFieldsArray} */
    private static int fieldOffset(int fileId, int fieldNo) {
      if (fileId <= 0) {
        throw new IllegalArgumentException("invalid fileId: " + fileId);
      }
      assert (0 <= fieldNo && fieldNo < INT_FIELDS_COUNT) : "fieldNo(=" + fieldNo + ") must be in [0," + INT_FIELDS_COUNT + ")";
      return ((fileId & OFFSET_MASK) * INT_FIELDS_COUNT) + fieldNo;
    }

    /** @return human-readable description of file fileId -- as much information as VFS now contains */
    private @NotNull String describeAlreadyCreatedFile(int fileId) {
      FSRecordsImpl vfsPeer = owningVfsData.owningPersistentFS.peer();
      int parentId = vfsPeer.getParent(fileId);
      int nameId = vfsPeer.getNameIdByFileId(fileId);
      String fileName = vfsPeer.getNameByNameId(nameId);
      String description = "fileId=" + fileId +
                           "; nameId=" + nameId + "(" + fileName + ")" +
                           "; parentId=" + parentId;
      if (parentId > 0) {
        description += "; parent.name=" + vfsPeer.getName(parentId)
                       + "; parent.children=" + vfsPeer.list(parentId) + "; ";
      }
      return description;
    }
  }

  /**
   * This class is mostly a data-holder: most operations are in {@link VirtualDirectoryImpl}.
   *
   * Non-final field modifications are synchronized on 'this' instance (but this is done in {@link VirtualDirectoryImpl})
   */
  @ApiStatus.Internal
  public static final class DirectoryData {
    private static final AtomicFieldUpdater<DirectoryData, KeyFMap> USER_MAP_UPDATER =
      AtomicFieldUpdater.forFieldOfType(DirectoryData.class, KeyFMap.class);
    volatile @NotNull KeyFMap userMap = KeyFMap.EMPTY_MAP;
    /**
     * assigned under lock(this) only; never modified in-place (=uses copy-on-write)
     *
     * @see VirtualDirectoryImpl#findIndexByName(ChildrenIds, CharSequence, boolean)
     */
    //MAYBE RC:we don't really need to always _load and keep_ the children in memory. We could always load them from
    //          FSRecordsImpl, and we could even iterate/search through FSRecordsImpl-stored children directly, unpacking
    //          diff-compressed data on the way. This shouldn't be much slower than linear-search in in-memory int[],
    //          but it allows to not waste memory on children lists that are not needed, which may be substantial
    //          given: 1) we _never unload_ VfsData cache 2) most of VirtualDirectory we load we load _not_ to iterate
    //          through it's children, but just to build a hierarchy, to access some leaf-file, e.g. during indexing
    //          or during indexes lookups -- so we'll rarely/never actually use this VirtualDirectory.children.
    volatile @NotNull ChildrenIds children = ChildrenIds.EMPTY;

    /** assigned under lock(this) only; accessed/modified map contents under lock(adoptedNames) */
    private volatile Set<CharSequence> adoptedNames;

    boolean changeUserMap(@NotNull KeyFMap oldMap, @NotNull KeyFMap newMap) {
      return USER_MAP_UPDATER.compareAndSet(this, oldMap, newMap);
    }

    boolean isAdoptedName(@NotNull CharSequence name) {
      Set<CharSequence> adopted = adoptedNames;
      if (adopted == null) {
        return false;
      }
      synchronized (adopted) {
        return adopted.contains(name);
      }
    }

    /**
     * must call removeAdoptedName() before adding new child with the same name
     * or otherwise {@link VirtualDirectoryImpl#doFindChild(String, boolean, NewVirtualFileSystem, boolean)} would risk finding already non-existing child
     * <p>
     * Must be called in synchronized(VfsData)
     */
    void removeAdoptedName(@NotNull CharSequence name) {
      Set<CharSequence> adopted = adoptedNames;
      if (adopted == null) {
        return;
      }
      synchronized (adopted) {
        boolean removed = adopted.remove(name);
        if (removed && adopted.isEmpty()) {
          adoptedNames = null;
        }
      }
    }

    /**
     * Must be called in synchronized(VfsData)
     */
    void addAdoptedName(@NotNull String name, boolean caseSensitive) {
      Set<CharSequence> adopted = getOrCreateAdoptedNames(caseSensitive);
      synchronized (adopted) {
        adopted.add(name);
      }
    }

    /**
     * Optimization: faster than call {@link #addAdoptedName(String, boolean)} one by one
     * Must be called in synchronized(VfsData)
     */
    void addAdoptedNames(@NotNull Collection<? extends CharSequence> names, boolean caseSensitive) {
      Set<CharSequence> adopted = getOrCreateAdoptedNames(caseSensitive);
      synchronized (adopted) {
        adopted.addAll(names);
      }
    }

    /**
     * Must be called in synchronized(VfsData)
     */
    private @NotNull Set<CharSequence> getOrCreateAdoptedNames(boolean caseSensitive) {
      Set<CharSequence> adopted = adoptedNames;
      if (adopted == null) {
        adopted = CollectionFactory.createCharSequenceSet(caseSensitive);
        adoptedNames = adopted;
      }
      return adopted;
    }

    @NotNull @Unmodifiable List<String> getAdoptedNames() {
      Set<CharSequence> adopted = adoptedNames;
      if (adopted == null) return Collections.emptyList();
      synchronized (adopted) {
        return ContainerUtil.map(adopted, Functions.TO_STRING());
      }
    }

    /**
     * Must be called in synchronized(VfsData)
     */
    void clearAdoptedNames() {
      adoptedNames = null;
    }

    @Override
    public @NonNls String toString() {
      return "DirectoryData{" +
             "userMap=" + userMap +
             ", children=" + children +
             ", adoptedNames=" + adoptedNames +
             '}';
    }
  }

  public static final class FileAlreadyCreatedException extends RuntimeException {
    private FileAlreadyCreatedException(@NotNull String message) {
      super(message);
    }
  }

  @ApiStatus.Internal
  public static final class ChildrenIds {
    public static final ChildrenIds EMPTY = new ChildrenIds(ArrayUtilRt.EMPTY_INT_ARRAY, /*sorted:*/ true, /*allLoaded: */ false);

    private static final byte SORTED_BY_NAME_MASK = 0b01;
    private static final byte ALL_CHILDREN_LOADED_MASK = 0b10;

    private final int[] ids;
    /** bitmask: SORTED_BY_NAME_MASK | ALL_CHILDREN_LOADED_MASK */
    private final int flags;


    public ChildrenIds(int[] ids,
                       boolean sortedByName,
                       boolean allChildrenLoaded) {
      this(ids, (sortedByName ? SORTED_BY_NAME_MASK : 0) | (allChildrenLoaded ? ALL_CHILDREN_LOADED_MASK : 0));
    }

    private ChildrenIds(int[] ids,
                        int flags) {
      this.ids = ids;
      this.flags = flags;
    }

    public int size() {
      return ids.length;
    }

    public int id(int index) {
      return ids[index];
    }

    public boolean isSorted() {
      return (flags & SORTED_BY_NAME_MASK) != 0;
    }

    public boolean areAllChildrenLoaded() {
      return (flags & ALL_CHILDREN_LOADED_MASK) != 0;
    }

    public IntOpenHashSet toIntSet() {
      return new IntOpenHashSet(ids);
    }

    public VirtualFileSystemEntry @NotNull [] asFiles(@NotNull IntFunction<? extends VirtualFileSystemEntry> fileLoader) {
      VirtualFileSystemEntry[] children = new VirtualFileSystemEntry[ids.length];
      for (int i = 0; i < ids.length; i++) {
        int id = ids[i];
        VirtualFileSystemEntry child = fileLoader.apply(id);
        if (child == null) {
          throw new AssertionError("Bug: can't load file by id " + id);
        }
        children[i] = child;
      }
      return children;
    }


    public @NotNull ChildrenIds withAllChildrenLoaded(boolean allChildrenLoaded) {
      if (areAllChildrenLoaded() == allChildrenLoaded) {
        return this;
      }
      return new ChildrenIds(ids, isSorted(), allChildrenLoaded);
    }

    public @NotNull ChildrenIds withIds(int[] updatedIds) {
      return new ChildrenIds(updatedIds, flags);
    }

    /** @return children sorted with the supplied comparator and fileLoader, regardless of current .sortedByName value */
    public ChildrenIds sorted(@NotNull IntFunction<? extends VirtualFileSystemEntry> fileLoader,
                              @NotNull Comparator<? super VirtualFileSystemEntry> comparator) {
      //Since fileLoader/comparator is supplied externally, we can't rely on .sortedByName  -- it should be checked
      // by this method's caller, and it's up to the caller to decide to trust it or not
      if (ids.length <= 1) {
        return new ChildrenIds(ids, /*sorted: */ true, areAllChildrenLoaded());
      }

      VirtualFileSystemEntry[] files = asFiles(fileLoader);
      ContainerUtil.sort(files, comparator);

      int[] sortedIds = new int[ids.length];
      for (int i = 0; i < files.length; i++) {
        sortedIds[i] = files[i].getId();
      }
      return new ChildrenIds(sortedIds, /*sorted: */ true, areAllChildrenLoaded());
    }


    /** linear O(N) search, -1 if not found */
    public int indexOfId(int id) {
      return ArrayUtil.indexOf(ids, id);
    }

    /**
     * @return index of child with given name, with given namesComparator and namesLoader(fileId->fileName).
     * If child with given name is not found, returns standard for binary search (-insertionIndex-1)
     */
    public int findIndexByName(@NotNull CharSequence name,
                               @NotNull Comparator<? super CharSequence> namesComparator,
                               @NotNull IntFunction<? extends CharSequence> nameLoader) {
      if (!isSorted()) {
        throw new IllegalStateException("Children must be sorted for binary search");
      }
      return ObjectUtils.binarySearch(
        0, ids.length,
        mid -> namesComparator.compare(nameLoader.apply(ids[mid]), name)
      );
    }


    public @NotNull ChildrenIds insertAt(int index, int id) {
      int[] updatedIds = ArrayUtil.insert(ids, index, id);
      return withIds(updatedIds);
    }

    public @NotNull ChildrenIds appendId(int id) {
      //if we append id -- most likely 'sorted' property is lost:
      return appendId(id, /*stillSorted: */ false);
    }

    public @NotNull ChildrenIds appendId(int id, boolean stillSorted) {
      int[] updatedIds = ArrayUtil.append(ids, id);
      return new ChildrenIds(updatedIds, stillSorted, areAllChildrenLoaded());
    }

    public @NotNull ChildrenIds removeAt(int index) {
      int[] updatedIds = ArrayUtil.remove(ids, index);
      return withIds(updatedIds);
    }

    public @NotNull ChildrenIds removeIds(@NotNull IntSet idsToRemove) {
      int[] newIds = new int[ids.length];
      int newIdsCount = 0;
      for (int id : ids) {
        if (!idsToRemove.contains(id)) {
          newIds[newIdsCount++] = id;
        }
      }
      if (newIdsCount == newIds.length) {//no ids were skipped:
        return this;
      }

      newIds = (newIdsCount == 0) ? ArrayUtil.EMPTY_INT_ARRAY : Arrays.copyOf(newIds, newIdsCount);
      return withIds(newIds);
    }

    @Override
    public String toString() {
      return "Children[ids: " + Arrays.toString(ids) + ", sortedByName: " + isSorted() + ", allLoaded: " + areAllChildrenLoaded() + "]";
    }
  }
}
