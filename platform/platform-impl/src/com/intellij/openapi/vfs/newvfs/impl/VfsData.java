// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.impl;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationListener;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.InvalidVirtualFileAccessException;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecordsImpl;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl;
import com.intellij.platform.diagnostic.telemetry.PlatformScopesKt;
import com.intellij.platform.diagnostic.telemetry.TelemetryManager;
import com.intellij.util.*;
import com.intellij.util.concurrency.AtomicFieldUpdater;
import com.intellij.util.containers.*;
import com.intellij.util.keyFMap.KeyFMap;
import io.opentelemetry.api.metrics.BatchCallback;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableDoubleMeasurement;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.*;

import java.io.Closeable;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import java.util.function.IntUnaryOperator;

import static com.intellij.util.SystemProperties.getBooleanProperty;

/**
 * Main part of VFS in-memory cache: flags, user data and children are all stored here.
 * {@link VirtualFileSystemEntry} and {@link VirtualDirectoryImpl} objects mainly just store fileId, so they could be
 * seen as 'pointers' into this cache.
 * <p>
 * The purpose is to avoid holding this data in separate immortal file/directory objects because that involves space overhead,
 * significant when there are hundreds of thousands of files.
 * <p>
 * The data is stored per-id in blocks of {@link #SEGMENT_SIZE}. File ids in one project tend to cluster together, so the
 * overhead for non-loaded id should not be large in most cases.
 * <p>
 * File objects are still created if needed. There might be several objects for the same file, so equals() should be used
 * instead of ==.
 * <p>
 * The lifecycle of a file object is as follows:
 * <ol>
 * <li> The file has not been instantiated yet, so {@link #cachedFileById} returns null. </li>
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
public final class VfsData implements Closeable {
  private static final Logger LOG = Logger.getInstance(VfsData.class);

  /**
   * If true, {@link #segments} map uses soft-references, i.e., GC may squeeze unused entries, preventing it from
   * growing too large. <p/>
   * If false (legacy option) -- {@link #segments} map is strong-reference based, i.e., entries are never evicted, and the
   * map only grows. <p/>
   * (This option may look like a regular cause of memory issues -- but practice proves otherwise: it was the only option
   * for years, and caused the memory issues only occasionally. The reason for that is that overall files number in VFS is
   * limited in most use-cases, and only a fraction of all VFS files is really accessed in a single IDE session. Thus, VFS
   * cache size without an eviction is still typically in the range 20-50Mbs for the intellij project -- which is tolerable.
   * I.e., this option definitely _could_ be used in practice)
   */
  private static final boolean USE_SOFT_REFERENCES = getBooleanProperty("platform.vfs.cache.use-soft-references", false);

  private static final int SEGMENT_BITS = 9;
  private static final int SEGMENT_SIZE = 1 << SEGMENT_BITS;
  private static final int OFFSET_MASK = SEGMENT_SIZE - 1;

  private final Application app;
  private final PersistentFSImpl owningPersistentFS;

  /**
   * Mark 'invalidated' (=deleted) files in {@link Segment#objectFieldsArray}.
   * Also used as a lock to protect {@link #queueOfFileIdsToBeInvalidated}
   */
  private final Object deadMarker = ObjectUtils.sentinel("dead file");

  //TODO RC: FSRecords was quite optimized recently, probably caching is not needed anymore?
  //         indexingFlag/nameId caching was already removed -- need to think through about remaining (flag+modCount)
  //         field: on the first sight they look like an additional data, independent from persistent VFS data?
  //         .children is another thing that could be less cached -- in many cases children could be accessed directly from
  //         FSRecords?

  /**
   * Map[segmentIndex -> Segment]
   * <p/>
   * Notes for the case the map is SoftReference-based: {@link VirtualFileSystemEntry#segment} is a backref to the {@link Segment}
   * that contains the entry's data -- which means that segment is strongly-reachable if at least one {@link VirtualFileSystemEntry},
   * backed by this segment, is strongly reachable (which includes {@link VirtualFileSystemEntry#parent} field!).
   * So GC is free to collect only the segments that are not used by any {@link VirtualFileSystemEntry} currently in use
   * (i.e., strongly-reachable). Which seems to be quite intuitive behavior.
   */
  private final ConcurrentIntObjectMap<Segment> segments = USE_SOFT_REFERENCES ?
                                                           ConcurrentCollectionFactory.createConcurrentIntObjectSoftValueMap() :
                                                           ConcurrentCollectionFactory.createConcurrentIntObjectMap();

  /**
   * Set of deleted file ids. Never cleaned during a session (deleted fileId could be re-used, but it could be done only on
   * a session start, see {@link FSRecordsImpl}).
   * File ids are added to this set immediately on file deletion, while _also_ added to the {@link #queueOfFileIdsToBeInvalidated},
   * to be later (after-WA) used to replace the file's data {@link Segment#objectFieldsArray} with {@link #deadMarker}.
   * MAYBE RC: maybe steal a bit for it in {@link Segment#intFieldsArray}? 16M->8M modCounts seems to be not very critical.
   *           Or FSRecords.isDeleted could be used -- it would be even better, since it reduces data duplication: now
   *           we have 'persistent file record marked deleted' and 'in-memory file record marked as invalid', but they could
   *           be merged into one.
   */
  private final ConcurrentBitSet invalidatedFileIds = ConcurrentBitSet.create();

  /**
   * Records (fileIds) to be invalidated: removed from {@link #changedParents} and set 'dead' in {@link Segment#objectFieldsArray}).
   * As soon, as apt slots are processed, this queue is cleared.
   * Guarded by {@link #deadMarker}
   */
  private IntSet queueOfFileIdsToBeInvalidated = new IntOpenHashSet();

  /**
   * If file/directory is moved (==parent is changed), then the change is added to this map, as (originalParentId -> newParent).
   *
   * @see Segment#replacement
   * @see VirtualFileSystemEntry#updateSegmentAndParent(Segment)
   */
  private final IntObjectMap<VirtualDirectoryImpl> changedParents = ConcurrentCollectionFactory.createConcurrentIntObjectMap();


  private final Disposable writeActionListenerDisposer = Disposer.newDisposable();

  // ====================== monitoring ======================================================================================= //

  /** # of {@link Segment} instances created, since the start */
  private final AtomicInteger segmentsCreated = new AtomicInteger();

  /** # of {@link DirectoryData}/{@link VirtualDirectoryImpl} objects created, since the start */
  private final AtomicInteger directoriesCreated = new AtomicInteger();

  /** # of {@link VirtualFileImpl} objects created, since the start */
  private final AtomicInteger filesCreated = new AtomicInteger();

  private final BatchCallback otelHandle;

  public VfsData(@NotNull Application app,
                 @NotNull PersistentFSImpl pfs) {
    this.app = app;
    this.owningPersistentFS = pfs;

    LOG.info("Use SoftReference in VFS cache: " + USE_SOFT_REFERENCES);

    //TODO RC: replace with ((ApplicationEx)app).addWriteActionListener(new WriteActionListener()) ?
    app.addApplicationListener(new ApplicationListener() {
      @Override
      public void writeActionFinished(@NotNull Object action) {
        // after top-level write action is finished, all the deletion listeners should have processed the deleted files
        // and their data is considered safe to remove. From this point on accessing a removed file will result in an exception.
        if (!app.isWriteAccessAllowed()) {
          killInvalidatedFiles();
        }
      }
    }, writeActionListenerDisposer);

    otelHandle = setupOTelMonitoring();
  }

  private BatchCallback setupOTelMonitoring() {
    Meter vfsMeter = TelemetryManager.getInstance().getMeter(PlatformScopesKt.VFS);
    ObservableDoubleMeasurement cacheSegmentsCount = vfsMeter.gaugeBuilder("VFS.cache.segments").buildObserver();
    ObservableLongMeasurement cacheSegmentsCreated = vfsMeter.counterBuilder("VFS.cache.segmentsCreated").buildObserver();
    ObservableLongMeasurement cacheDirectoriesCreated = vfsMeter.counterBuilder("VFS.cache.directoriesCreated").buildObserver();
    ObservableLongMeasurement cacheFilesCreated = vfsMeter.counterBuilder("VFS.cache.filesLoaded").buildObserver();
    return vfsMeter.batchCallback(
      () -> {
        cacheSegmentsCount.record(segments.size());
        cacheSegmentsCreated.record(segmentsCreated.get());
        cacheDirectoriesCreated.record(directoriesCreated.get());
        cacheFilesCreated.record(filesCreated.get());
      },
      cacheSegmentsCount, cacheSegmentsCreated,
      cacheDirectoriesCreated, cacheFilesCreated
    );
  }

  @Override
  public void close() {
    otelHandle.close();
    Disposer.dispose(writeActionListenerDisposer);
  }

  @NotNull PersistentFSImpl owningPersistentFS() {
    return owningPersistentFS;
  }

  private void killInvalidatedFiles() {
    synchronized (deadMarker) {
      if (!queueOfFileIdsToBeInvalidated.isEmpty()) {
        for (IntIterator iterator = queueOfFileIdsToBeInvalidated.iterator(); iterator.hasNext(); ) {
          int id = iterator.nextInt();
          Segment segment = Objects.requireNonNull(segmentForFileId(id, /*create: */false));
          segment.killFileData(id, deadMarker);
          changedParents.remove(id);
        }
        queueOfFileIdsToBeInvalidated = new IntOpenHashSet();
      }
    }
  }

  /**
   * @return a {@link VirtualFileSystemEntry} wrapper for the file data in the cache ({@link #segments}).
   * If there is no data in {@link #segments} cache for given id yet -- returns null.
   * If the file with given id was 'just deleted' (i.e. in the current WA) -- returns the wrapper what is {@code !isValid()},
   * but if the file was deleted in already finished WA -- throws {@link InvalidVirtualFileAccessException}
   */
  @Nullable VirtualFileSystemEntry cachedFileById(int id, @NotNull VirtualDirectoryImpl parent) {
    Segment segment = segmentForFileId(id, /*create: */ false);
    if (segment == null) return null;

    Object entryData = segment.fileDataById(id);
    if (entryData == null) return null;

    if (entryData == deadMarker) {
      throw reportDeadFileAccess(new VirtualFileImpl(id, segment, parent));
    }

    if (entryData instanceof DirectoryData directoryData) {
      VirtualDirectoryImpl directory = directoryData.directory;
      if (directory == null) {
        throw new AssertionError("Bug: " + directoryData + " must have .directory != null set at initialization!");
      }

      if (directory.getId() != id) {
        throw new AssertionError(
          "Bug: cachedFileById(" + id + ") returns " + directory + " with different id(=" + directory.getId() + ")"
        );
      }

      return directory;
    }

    filesCreated.incrementAndGet();
    return new VirtualFileImpl(id, segment, parent);
  }

  public @Nullable VirtualDirectoryImpl cachedDir(int id) {
    Segment segment = segmentForFileId(id, /*create: */ false);
    if (segment == null) return null;

    Object entryData = segment.fileDataById(id);
    if (entryData == null) return null;

    if (entryData == deadMarker) {
      return null;
    }

    if (entryData instanceof DirectoryData directoryData) {
      VirtualDirectoryImpl dir = directoryData.directory;
      if (dir != null && !dir.isValid()) {
        return null;
      }
      return dir;
    }
    return null;
  }

  public @NotNull Iterable<? extends VirtualFileSystemEntry> getCachedDirs() {
    List<VirtualFileSystemEntry> cachedDirs = new ArrayList<>();
    for (final Segment segment : segments.values()) {
      Object[] fileDataEntries = segment.objectFieldsArray;
      int length = fileDataEntries.length;
      for (int i = 0; i < length; i++) {
        Object entry = Segment.OBJECT_FIELDS_HANDLE.getVolatile(fileDataEntries, i);
        if (entry instanceof DirectoryData directoryData) {
          VirtualDirectoryImpl directory = directoryData.directory;
          if (directory != null && directory.isValid()) {
            cachedDirs.add(directory);
          }
        }
      }
    }
    return cachedDirs;
  }

  private static @NotNull InvalidVirtualFileAccessException reportDeadFileAccess(@NotNull VirtualFileSystemEntry file) {
    return new InvalidVirtualFileAccessException("Accessing dead virtual file: " + file.getUrl());
  }

  @Contract("_,true->!null")
  public Segment segmentForFileId(int fileId, boolean create) {
    int segmentIndex = segmentIndex(fileId);
    Segment segment = segments.get(segmentIndex);
    if (segment != null || !create) {
      return segment;
    }
    return segments.cacheOrGet(segmentIndex, new Segment(segmentIndex, this));
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

  /** Caches info about {@link #SEGMENT_SIZE} consequent files, indexed by {@code (fileId % SEGMENT_SIZE)} */
  @ApiStatus.Internal
  public static final class Segment {
    private static final int INT_FIELDS_COUNT = 1;
    private static final int FLAGS_AND_MOD_COUNT_FIELD_NO = 0;

    private static final VarHandle OBJECT_FIELDS_HANDLE;
    private static final VarHandle INT_FIELDS_HANDLE;

    static {
      try {
        OBJECT_FIELDS_HANDLE = MethodHandles.arrayElementVarHandle(Object[].class).withInvokeExactBehavior();
        INT_FIELDS_HANDLE = MethodHandles.arrayElementVarHandle(int[].class).withInvokeExactBehavior();
      }
      catch (Throwable t) {
        throw new ExceptionInInitializerError(t);
      }
    }

    /** Sequential index of the segment: only for debug/logging */
    private final int segmentIndex;

    private final VfsData owningVfsData;

    /** user data (KeyFMap) for files, {@link DirectoryData} for folders */
    private final Object[] objectFieldsArray;

    /**
     * [flags(:8bits) | modCount(:24bits)] per fileId
     * Currently it is single int32 per fileId: flag bits (highest byte) and modificationCounter (lowest 3 bytes)
     * Flags are from {@link VirtualFileSystemEntry.VfsDataFlags}: they are a subset of underlying {@link PersistentFS.Flags},
     * see {@link VirtualDirectoryImpl#initializeChildData(int, int, int, boolean)} for an assignment.
     * Modification counter is separated from underlying {@link FSRecordsImpl#getModCount(int)}: it is transient (not persistent),
     * and incremented only on file _content_ modification, while {@link FSRecordsImpl#getModCount(int)} is incremented on _any_
     * file attribute/content/etc change.
     */
    private final int[] intFieldsArray;


    /**
     * The reference is synchronized by read-write lock; clients outside read-action deserve to get outdated result
     * MAYBE RC: use volatile instead of relying to RA/WA somewhere up the stack?
     * <p/>
     * This replacement serves as a _signal_ for instances of {@link VirtualFileSystemEntry} instances (which could be many!)
     * to check are their parents updated via {@link VfsData#getChangedParent(int)}. The replacement segment is exactly the
     * same, as the original one -- they share all the same data -- only the Segment instance is different, which makes it
     * possible to deliver signal 'some parent(s) in this segment is/are changed' for all the {@link VirtualFileSystemEntry}
     * instances to get.
     *
     * @see VirtualFileSystemEntry#updateSegmentAndParent(Segment)
     */
    @Nullable Segment replacement;

    @VisibleForTesting
    public Segment(int segmentIndex,
                   @NotNull VfsData owningVfsData) {
      this(segmentIndex,
           owningVfsData,
           new Object[SEGMENT_SIZE],
           new int[SEGMENT_SIZE * INT_FIELDS_COUNT]
      );
    }

    /** Copying ctor */
    private Segment(int segmentIndex,
                    @NotNull VfsData owningVfsData,
                    Object @NotNull [] objectFieldsArray,
                    int @NotNull [] intFieldsArray) {
      this.segmentIndex = segmentIndex;
      this.objectFieldsArray = objectFieldsArray;
      this.intFieldsArray = intFieldsArray;
      this.owningVfsData = owningVfsData;

      owningVfsData.segmentsCreated.incrementAndGet();
    }

    @NotNull VfsData owningVfsData(){
      return owningVfsData;
    }

    void setUserMap(int fileId, @NotNull KeyFMap map) {
      int index = objectOffsetInSegment(fileId);
      Object oldMap = OBJECT_FIELDS_HANDLE.getAndSet(objectFieldsArray, index, (Object)map);
      if (oldMap == null) {
        //Entry in the objectFieldsArray must be initialized first in .initFileData(), during
        // VirtualFile lookup -- only after that VirtualFile could be made publicly available,
        // and this method is available to call. This seems to be violated:
        throw new IllegalStateException("file[#" + fileId + "]: cache record wasn't initialized yet, but already used (set " + map + ")");
      }
    }

    @NotNull KeyFMap getUserMap(@NotNull VirtualFileSystemEntry file, int id) {
      Object o = fileDataById(id);
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
      int offset = objectOffsetInSegment(fileId);
      return OBJECT_FIELDS_HANDLE.compareAndSet(objectFieldsArray, offset, (Object)oldMap, (Object)newMap);
    }

    boolean getFlag(int fileId, @VirtualFileSystemEntry.Flags int mask) {
      BitUtil.assertOneBitMask(mask);
      assert (mask & ~VirtualFileSystemEntry.ALL_FLAGS_MASK) == 0 : "Unexpected flag";

      int offset = fieldOffset(fileId, FLAGS_AND_MOD_COUNT_FIELD_NO);
      int flags = (int)INT_FIELDS_HANDLE.getVolatile(intFieldsArray, offset);
      return (flags & mask) != 0;
    }

    void setFlag(int fileId, @VirtualFileSystemEntry.Flags int mask, boolean value) {
      if (LOG.isTraceEnabled()) {
        LOG.trace("Set flag " + Integer.toHexString(mask) + "=" + value + " for id=" + fileId);
      }
      assert (mask & ~VirtualFileSystemEntry.ALL_FLAGS_MASK) == 0 : "Unexpected flag";
      int offset = fieldOffset(fileId, FLAGS_AND_MOD_COUNT_FIELD_NO);
      updateFlagsAtomically(offset, oldFlags -> BitUtil.set(oldFlags, mask, value));
    }

    private void updateFlagsAtomically(int offset, @NotNull IntUnaryOperator op) {
      while (true) {//CAS loop
        int oldFlags = (int)INT_FIELDS_HANDLE.getVolatile(intFieldsArray, offset);
        int newFlags = op.applyAsInt(oldFlags);
        if (INT_FIELDS_HANDLE.compareAndSet(intFieldsArray, offset, oldFlags, newFlags)) {
          return;
        }
      }
    }

    void setFlags(int fileId, @VirtualFileSystemEntry.Flags int combinedMask, @VirtualFileSystemEntry.Flags int combinedValue) {
      if (LOG.isTraceEnabled()) {
        LOG.trace("Set flags " + Integer.toHexString(combinedMask) + "=" + combinedValue + " for id=" + fileId);
      }
      assert (combinedMask & ~VirtualFileSystemEntry.ALL_FLAGS_MASK) == 0 : "Unexpected flag";
      assert (~combinedMask & combinedValue) == 0 : "Value (" + Integer.toHexString(combinedValue) + ") set bits outside mask (" +
                                                    Integer.toHexString(combinedMask) + ")";
      int offset = fieldOffset(fileId, FLAGS_AND_MOD_COUNT_FIELD_NO);
      updateFlagsAtomically(offset, oldFlags -> oldFlags & ~combinedMask | combinedValue);
    }

    /**
     * Transient content modification counter: different from {@link FSRecordsImpl#getModCount(int)} -- it is not persistent,
     * and incremented only on file _content_ modification, while {@link FSRecordsImpl#getModCount(int)} is incremented on _any_
     * file attribute/content/etc change.
     */
    long getModificationStamp(int fileId) {
      int offset = fieldOffset(fileId, FLAGS_AND_MOD_COUNT_FIELD_NO);
      int flags = (int)INT_FIELDS_HANDLE.getVolatile(intFieldsArray, offset);
      return flags & ~VirtualFileSystemEntry.ALL_FLAGS_MASK;
    }

    void setModificationStamp(int fileId, long stamp) {
      int offset = fieldOffset(fileId, FLAGS_AND_MOD_COUNT_FIELD_NO);
      updateFlagsAtomically(offset, oldFlags -> (oldFlags & VirtualFileSystemEntry.ALL_FLAGS_MASK) |
                                                ((int)stamp & ~VirtualFileSystemEntry.ALL_FLAGS_MASK));
    }

    void changeParent(int fileId, VirtualDirectoryImpl directory) {
      int segmentIndex = segmentIndex(fileId);

      assert replacement == null;
      replacement = new Segment(this.segmentIndex, owningVfsData, objectFieldsArray, intFieldsArray);
      boolean replaced = owningVfsData.segments.replace(segmentIndex, this, replacement);
      assert replaced;

      owningVfsData.changeParent(fileId, directory);
    }

    //@GuardedBy("parent.directoryData")
    void initFileData(int fileId, @NotNull Object fileData, @Nullable VirtualDirectoryImpl parent) throws FileAlreadyCreatedException {
      int offset = objectOffsetInSegment(fileId);
      if (fileData instanceof DirectoryData) {
        owningVfsData.directoriesCreated.incrementAndGet();
      }
      Object existingData = OBJECT_FIELDS_HANDLE.compareAndExchange(objectFieldsArray, offset, /*expected: */(Object)null, fileData);
      if (existingData != null) {
        //RC: it seems like concurrency issue, but I can't find a specific location
        //MAYBE RC: why it is even an error? It seems, like this could happen if the cached file entry was dropped by GC
        //          (it is a soft-ref), or sometimes just by concurrency. Maybe if an entry was already created -- so be
        //          it, log warn and go on?

        FSRecordsImpl vfsPeer = owningVfsData.owningPersistentFS.peer();
        int parentId = vfsPeer.getParent(fileId);
        DirectoryData parentData;
        if (parentId == FSRecords.NULL_FILE_ID) {// => fileId is root
          parentData = null;
        }
        else {
          Segment parentSegment = owningVfsData.segmentForFileId(parentId, false);
          parentData = (DirectoryData)parentSegment.fileDataById(parentId);
        }

        throw new FileAlreadyCreatedException(
          describeAlreadyCreatedFile(fileId)
          +
          " data: " +
          fileData
          +
          ", alreadyExistingData: " +
          existingData
          +
          ", parentData: " +
          parentData
          +
          ((parent != null)
           ? ", parent.data: " + parent.directoryData + " equals: " + (parentData == parent.directoryData)
           : ", parent = null")
          +
          ", synchronized(parentData): " +
          (parentData != null ? Thread.holdsLock(parentData) : "...")
        );
      }
    }

    Object fileDataById(int fileId){
      int offset = objectOffsetInSegment(fileId);
      return OBJECT_FIELDS_HANDLE.getVolatile(objectFieldsArray, offset);
    }

    void killFileData(int fileId, Object deadMarker) {
      OBJECT_FIELDS_HANDLE.setVolatile(objectFieldsArray, objectOffsetInSegment(fileId), deadMarker);
      //skip cleaning .intFieldsArray since dead marker means slot is _not reusable_ in this session
    }

    @Override
    public String toString() {
      return "Segment[#" + segmentIndex + "][replacement: " + replacement + "]";
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
   * <p>
   * Non-final field modifications are synchronized on 'this' instance (but this is done in {@link VirtualDirectoryImpl})
   */
  @ApiStatus.Internal
  public static final class DirectoryData {
    private static final AtomicFieldUpdater<DirectoryData, KeyFMap> USER_MAP_UPDATER =
      AtomicFieldUpdater.forFieldOfType(DirectoryData.class, KeyFMap.class);

    //TODO RC: given that we now have a direct link to the VirtualDirectoryImpl, which has it's own .userMap (inherited from
    //         NewVirtualFile) -- do we need this field here? Maybe just use the field directly from the directory?
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

    /**
     * This field is effectively-final, it should be assigned right after the ctor, and never re-assigned.
     * TODO RC: probably, we should merge VirtualDirectoryImpl and DirectoryData into a single object, and get rid of this
     *          indirection and associated initialization mess
     * TODO RC: an alternative is to use SoftReference here, and permit GC to collect VirtualDirectoryImpl objects with all
     *          their ChildrenIds (potentially long)
     */
    private VirtualDirectoryImpl directory = null;

    public DirectoryData() {
    }

    public void assignDirectory(@NotNull VirtualDirectoryImpl directory) {
      if (this.directory != null) {
        throw new IllegalStateException(".directory(=" + this.directory + ") must not be re-assigned, but: " + directory);
      }
      this.directory = directory;
    }

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
     * or otherwise {@link VirtualDirectoryImpl#findChildImpl(String, boolean, boolean)} would risk finding already non-existing child
     * <p>
     * Must be called in synchronized(DirectoryData)
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

    /** Must be called in synchronized(DirectoryData) */
    void addAdoptedName(@NotNull String name, boolean caseSensitive) {
      Set<CharSequence> adopted = getOrCreateAdoptedNames(caseSensitive);
      synchronized (adopted) {
        adopted.add(name);
      }
    }

    /**
     * Optimization: faster than call {@link #addAdoptedName(String, boolean)} one by one
     * Must be called in synchronized(DirectoryData)
     */
    void addAdoptedNames(@NotNull Collection<? extends CharSequence> names, boolean caseSensitive) {
      Set<CharSequence> adopted = getOrCreateAdoptedNames(caseSensitive);
      synchronized (adopted) {
        adopted.addAll(names);
      }
    }

    /**
     * Must be called in synchronized(DirectoryData)
     */
    private @NotNull Set<CharSequence> getOrCreateAdoptedNames(boolean caseSensitive) {
      Set<CharSequence> adopted = adoptedNames;
      if (adopted == null) {
        adopted = CollectionFactory.createCharSequenceSet(caseSensitive);
        adoptedNames = adopted;
      }
      return adopted;
    }

    @Unmodifiable
    @NotNull List<String> getAdoptedNames() {
      Set<CharSequence> adopted = adoptedNames;
      if (adopted == null) return Collections.emptyList();
      synchronized (adopted) {
        return ContainerUtil.map(adopted, Functions.TO_STRING());
      }
    }

    /** Must be called in synchronized(DirectoryData) */
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

    public VirtualFileSystemEntry @NotNull [] asFiles(@NotNull IntFunction<? extends @NotNull VirtualFileSystemEntry> fileLoader) {
      VirtualFileSystemEntry[] children = new VirtualFileSystemEntry[ids.length];
      for (int i = 0; i < ids.length; i++) {
        int id = ids[i];
        VirtualFileSystemEntry child = fileLoader.apply(id);
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
    public ChildrenIds sorted(@NotNull IntFunction<? extends @NotNull VirtualFileSystemEntry> fileLoader,
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
                               @NotNull IntFunction<? extends @NotNull CharSequence> nameLoader) {
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
