// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.impl;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.WriteActionListener;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecordsImpl;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl;
import com.intellij.platform.diagnostic.telemetry.PlatformScopesKt;
import com.intellij.platform.diagnostic.telemetry.TelemetryManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.BitUtil;
import com.intellij.util.Functions;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.AtomicFieldUpdater;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ConcurrentBitSet;
import com.intellij.util.containers.ConcurrentIntObjectMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.IntObjectMap;
import com.intellij.util.keyFMap.KeyFMap;
import io.opentelemetry.api.metrics.BatchCallback;
import io.opentelemetry.api.metrics.Meter;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.Closeable;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import java.util.function.IntUnaryOperator;

import static com.intellij.util.SystemProperties.getBooleanProperty;

/**
 * The main part of VFS in-memory cache: flags, user data and children are all stored here.
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
 * <li> If a file is deleted (={@link #invalidateFile(int)}), some of it's data could still be used by async listeners doing
 * some cleanup work -- so we can't clean all the file's data right away, and it is hard to tell the moment the cleanup becomes
 * possible. But we could clean _some_ pieces, like children for directories, because {@link VirtualFile#isValid()} explicitly
 * mentions that children are _not_ safe to access for invalid files. Other pieces, like UserData, is still accessible for as
 * long, as needed.
 * If {@link #USE_SOFT_REFERENCES} is enabled, than excess data could be collected by GC eventually, but only at {@link Segment}
 * granularity, see the apt comments.
 * </li>
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
   * ('false' option may look like a recipe for memory issues -- but practice proves otherwise: it was the only option
   * for years, and caused the memory issues only occasionally. The reason for that is that the overall files number in VFS
   * is limited in most use-cases, and only a fraction of all VFS files is really accessed in a single IDE session. Thus, VFS
   * cache size without an eviction is still typically in the range 20-50Mbs for the intellij project -- which is tolerable.
   * I.e., 'false' option definitely _could_ be used in practice)
   */
  private static final boolean USE_SOFT_REFERENCES = getBooleanProperty("platform.vfs.cache.use-soft-references", true);

  private static final int SEGMENT_BITS = 9;
  private static final int SEGMENT_SIZE = 1 << SEGMENT_BITS;
  private static final int OFFSET_MASK = SEGMENT_SIZE - 1;

  private final Application app;
  private final PersistentFSImpl owningPersistentFS;

  /**
   * Mark 'invalidated' (=deleted) files in {@link Segment#objectFieldsArray}.
   * Also used as a lock to protect {@link #fileIdsQueueForCleanup}
   */
  private final Object invalidatedQueueLock = ObjectUtils.sentinel("dead file");

  //TODO RC: FSRecords was quite optimized recently, maybe use it _instead_ of cache sometimes?
  //         0. indexingFlag/nameId caching was already removed, FSRecords used instead
  //         1. (flag+modCount) field: this is additional data, partially independent from persistent VFS data => can't be skipped
  //         2. .invalidatedFileIds -- FSRecords.isDeleted() could be used instead? See comments
  //         2. DirectoryData.children: in many cases children could be accessed directly from FSRecords, without need to cache them?

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
   * a new session start, see {@link FSRecordsImpl}).
   * File ids are added to this set immediately on file deletion, while _also_ added to the {@link #fileIdsQueueForCleanup},
   * to be later (after-WA) removed from {@link #changedParents}.
   * BEWARE: FSRecords.isDeleted flags are _not_ copied here -- instead, 'deleted' records from FSRecords are not loaded in cache
   * at all, so we never need appropriate bits to be set in invalidatedFileIds.
   * MAYBE RC: maybe steal a bit for it in {@link Segment#intFieldsArray}, e.g. from modCount -- 16M->8M modCounts seems
   *           to be not very critical. Or FSRecords.isDeleted could be used -- it would be even better, since it reduces data
   *           duplication: now we have 'persistent file record marked deleted' and 'in-memory file record marked as invalid',
   *           but they could be merged into one.
   */
  private final ConcurrentBitSet invalidatedFileIds = ConcurrentBitSet.create();

  /**
   * Invalidated records (fileIds) for some delayed cleanup.
   * Currently, the cleanup == remove from {@link #changedParents}.
   * As soon as cleanup is executed, this queue is cleared.
   */
  //@Guarded by {@link #invalidatedQueueLock}
  private IntSet fileIdsQueueForCleanup = new IntOpenHashSet();

  /**
   * If the file/directory is moved (==parent is changed), then the change is added to this map, as (originalParentId -> newParent).
   * This is needed because it could be >1 VirtualFile instances representing the same file, and all those instances need to know
   * somehow the parent has changed -- so they all check this collection on .getParent()
   *
   * @see Segment#replacement
   * @see VirtualFileSystemEntry#updateSegmentAndParent(Segment)
   */
  private final IntObjectMap<VirtualDirectoryImpl> changedParents = ConcurrentCollectionFactory.createConcurrentIntObjectMap();


  private final Disposable writeActionListenerDisposer = Disposer.newDisposable();

  // ====================== monitoring: ====================================================================================== //

  /** # of {@link Segment} instances created, over the lifespan of VfsData */
  private final AtomicInteger segmentsCreated = new AtomicInteger();

  /** # of {@link DirectoryData}/{@link VirtualDirectoryImpl} objects created, over the lifespan of VfsData */
  private final AtomicInteger directoriesCreated = new AtomicInteger();

  /** # of {@link VirtualFileImpl} objects created, over the lifespan of VfsData */
  private final AtomicInteger filesCreated = new AtomicInteger();

  private final BatchCallback otelHandle;


  public VfsData(@NotNull Application app,
                 @NotNull PersistentFSImpl pfs) {
    this.app = app;
    this.owningPersistentFS = pfs;

    LOG.info("Use SoftReference in VFS cache: " + USE_SOFT_REFERENCES);

    ((ApplicationEx)app).addWriteActionListener(new WriteActionListener() {
      @Override
      public void writeActionFinished(@NotNull Class<?> action) {
        cleanupInvalidatedFileRecords();
      }
    }, writeActionListenerDisposer);

    otelHandle = setupOTelMonitoring();
  }

  private BatchCallback setupOTelMonitoring() {
    Meter vfsMeter = TelemetryManager.getInstance().getMeter(PlatformScopesKt.VFS);
    var cacheSegmentsCount = vfsMeter.gaugeBuilder("VFS.cache.segments").ofLongs().buildObserver();
    var cacheSegmentsCreated = vfsMeter.counterBuilder("VFS.cache.segmentsCreated").buildObserver();
    var cacheDirectoriesCreated = vfsMeter.counterBuilder("VFS.cache.directoriesCreated").buildObserver();
    var cacheFilesCreated = vfsMeter.counterBuilder("VFS.cache.filesCreated").buildObserver();
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
    Disposer.dispose(writeActionListenerDisposer);
    otelHandle.close();
  }

  @NotNull PersistentFSImpl owningPersistentFS() {
    return owningPersistentFS;
  }

  private void cleanupInvalidatedFileRecords() {
    synchronized (invalidatedQueueLock) {
      if (!fileIdsQueueForCleanup.isEmpty()) {
        fileIdsQueueForCleanup.addAll(collectUnremovedChildren(fileIdsQueueForCleanup));

        fileIdsQueueForCleanup.forEach(fileId -> {
          Segment segment = segmentForFileId(fileId, /*create: */ false);
          if (segment != null) {//could be GCed already
            segment.cleanFileData(fileId);
          }

          changedParents.remove(fileId);
        });
        fileIdsQueueForCleanup = new IntOpenHashSet();
      }
    }
  }


  /// Normally, children should be removed before directory (see `PersistentFSImpl.invalidateSubtree()` )
  /// => removed children are added to [fileIdsQueueForCleanup] before removed directory => at this point
  /// `directoryData.children` must be empty -- and since we're in a WA, no one could load a new child
  /// until WA ends.
  /// But there is a couple of methods that could add childId to the .children outside RA:
  /// 1. [PersistentFSImpl#findChildInfo]: infamous 'local refresh' that violates RA/WA constraint
  ///    (='while WA is running no one else could modify the Model') and causes quite a lot of headaches
  ///    because of that -- specifically, it could load a new child in parallel with running WA.
  /// 2. [VirtualDirectoryImpl#findChildById]: also could add childId to .children without checking the directory
  ///    is still valid.
  /// (Maybe there are others, too)
  /// In both cases (re-)checking `dir.isValid()` under the directoryData lock could harm performance.
  /// So for now the solution is to re-check for not-yet-removed children here:
  private @NotNull IntSet collectUnremovedChildren(@NotNull IntSet fileIdsQueueForCleanup) {
    IntOpenHashSet fileIdsToCheck = new IntOpenHashSet(fileIdsQueueForCleanup);
    IntOpenHashSet fileIdsAlreadyChecked = new IntOpenHashSet(fileIdsToCheck.size());

    IntSet unremovedChildIds = null;
    IntOpenHashSet unremovedChildrenInTurn = new IntOpenHashSet(1);
    while (!fileIdsToCheck.isEmpty()) {
      unremovedChildrenInTurn.clear();
      for (IntIterator itr = fileIdsToCheck.intIterator(); itr.hasNext(); ) {
        int fileId = itr.nextInt();
        itr.remove();

        if (!fileIdsAlreadyChecked.add(fileId)) {
          continue;
        }

        Segment segment = segmentForFileId(fileId, /*create: */ false);
        if (segment != null) {//could be GCed already
          Object fileData = segment.fileDataById(fileId);
          if (fileData instanceof DirectoryData directoryData) {
            ChildrenIds childrenIds;
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (directoryData) {
              childrenIds = directoryData.children;
              if (!childrenIds.isInvalidated()) {
                continue;
              }
              directoryData.children = ChildrenIds.INVALIDATED;
              if (childrenIds.size() == 0) {
                continue;
              }
            }

            IntSet unremovedChildren = childrenIds.toIntSet();
            unremovedChildrenInTurn.addAll(unremovedChildren);
          }
        }
      }

      if (!unremovedChildrenInTurn.isEmpty()) {
        if (unremovedChildIds == null) {
          unremovedChildIds = new IntOpenHashSet(unremovedChildrenInTurn.size());
        }
        unremovedChildIds.addAll(unremovedChildrenInTurn);
        fileIdsToCheck.addAll(unremovedChildrenInTurn);
      }
    }
    return unremovedChildIds == null ?
           IntSet.of() :
           unremovedChildIds;
  }

  /**
   * @return a {@link VirtualFileSystemEntry} wrapper for the file data in the cache ({@link #segments}).
   * If there is no data in {@link #segments} cache for given id yet, or the file was deleted -- returns null.
   */
  @Nullable VirtualFileSystemEntry cachedFileById(int id, @NotNull VirtualDirectoryImpl parent) {
    if (!isFileValid(id)) {
      return null;
    }

    Segment segment = segmentForFileId(id, /*create: */ false);
    if (segment == null) return null;

    Object entryData = segment.fileDataById(id);
    if (entryData == null) return null;

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

  /**
   * @return a {@link VirtualDirectoryImpl} wrapper for the file data in the cache ({@link #segments}).
   * If there is no data in {@link #segments} cache for given id yet, or the directory was deleted -- returns null.
   */
  public @Nullable VirtualDirectoryImpl cachedDir(int id) {
    if (!isFileValid(id)) {
      return null;
    }

    Segment segment = segmentForFileId(id, /*create: */ false);
    if (segment == null) return null;

    Object entryData = segment.fileDataById(id);
    if (entryData == null) return null;

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
    synchronized (invalidatedQueueLock) {
      fileIdsQueueForCleanup.add(id);
    }
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "{segments: " + segments.size() + ", connected: " + owningPersistentFS.isConnected() + "}";
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

    @NotNull VfsData owningVfsData() {
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
        return KeyFMap.EMPTY_MAP;
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
          + " data: " + fileData + ", alreadyExistingData: " + existingData + ", parentData: " + parentData +
          ((parent != null) ?
           ", parent.data: " + parent.directoryData + " equals: " + (parentData == parent.directoryData) :
           ", parent = null"
          )
          + ", synchronized(parentData): " + (parentData != null ? Thread.holdsLock(parentData) : "...")
        );
      }
    }

    Object fileDataById(int fileId) {
      int offset = objectOffsetInSegment(fileId);
      return OBJECT_FIELDS_HANDLE.getVolatile(objectFieldsArray, offset);
    }

    void cleanFileData(int fileId) {
      //Ideally, we should replace the value with null or some placeholder value -- to release any garbage attached to the file's
      // userData for GC to collect.
      // But it turns out that already removed (invalidated) files could be still used for quite some time after their deletion,
      // and even after apt WA is finished. E.g. async/background listeners are often doing things like that.
      // Those uses often rely on some flags/keys in file.userData -- so file.userData is better to be preserved even after the
      // file is deleted -- and for unknown timespan.
      // And, unfortunately, the contract of file.isValid() doesn't prohibit that kind of use-after-delete: it is stated that
      // getUserData/putUserData _could_ still be used on invalid files. It doesn't directly state that userData is _preserved_
      // after the file is deleted, but it could be read this way -- so the file.userData use-after-delete is not an error.
      //
      // Hence, we're forced to keep the slot of the removed file intact -- until, maybe, the whole Segment is released at some
      // point, and GC collects it:
      Object value = OBJECT_FIELDS_HANDLE.getVolatile(objectFieldsArray, objectOffsetInSegment(fileId));
      if (value instanceof DirectoryData directoryData) {
        ChildrenIds children = directoryData.children;
        if (children.size() > 0) {
          //Children of a deleted directory _must_ be themselves deleted, too, even before the directory is deleted,
          // see PersistentFSImpl.invalidateSubtree()/invalidateNode()
          LOG.error("[#" + fileId + "].children is !empty (" + children + ") while the file is deleted!");
        }
      }
      //cleaning .intFieldsArray is not important, since deleted slots/files are not reusable in this session
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

    /**
     * Children's names known to be absent -- i.e., names that were looked up and not found.
     * Memorize them, to not repeat the same lookup again. Clear the memorized set on refresh.
     * Collection is assigned under lock(this) only; accessed/modified set content under lock(adoptedNames).
     */
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
    //@formatter:off
    private static final byte SORTED_BY_NAME_MASK       = 0b0001;
    private static final byte ALL_CHILDREN_LOADED_MASK  = 0b0010;
    private static final byte INVALIDATED_MASK          = 0b0100;
    //@formatter:on

    public static final ChildrenIds EMPTY = new ChildrenIds(ArrayUtilRt.EMPTY_INT_ARRAY, /*sorted:*/ true, /*allLoaded: */ false);
    public static final ChildrenIds INVALIDATED = new ChildrenIds(
      ArrayUtilRt.EMPTY_INT_ARRAY,
      SORTED_BY_NAME_MASK | ALL_CHILDREN_LOADED_MASK | INVALIDATED_MASK
    );

    private final int[] ids;
    /** bitmask: SORTED_BY_NAME_MASK | ALL_CHILDREN_LOADED_MASK | INVALIDATED_MASK */
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

    /// A deleted directory whose children list must not be repopulated.
    public boolean isInvalidated() {
      return (flags & INVALIDATED_MASK) != 0;
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
      if (isInvalidated()) {
        return this;
      }
      if (areAllChildrenLoaded() == allChildrenLoaded) {
        return this;
      }
      return new ChildrenIds(ids, isSorted(), allChildrenLoaded);
    }

    public @NotNull ChildrenIds withIds(int[] updatedIds) {
      if (isInvalidated()) {
        return this;
      }
      return new ChildrenIds(updatedIds, flags);
    }

    /** @return children sorted with the supplied comparator and fileLoader, regardless of current .sortedByName value */
    public ChildrenIds sorted(@NotNull IntFunction<? extends @NotNull VirtualFileSystemEntry> fileLoader,
                              @NotNull Comparator<? super VirtualFileSystemEntry> comparator) {
      if (isInvalidated()) {
        return this;
      }
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
      if (isInvalidated()) {
        return this;
      }
      int[] updatedIds = ArrayUtil.insert(ids, index, id);
      return withIds(updatedIds);
    }

    public @NotNull ChildrenIds appendId(int id) {
      //if we append id -- most likely 'sorted' property is lost:
      return appendId(id, /*stillSorted: */ false);
    }

    public @NotNull ChildrenIds appendId(int id, boolean stillSorted) {
      if (isInvalidated()) {
        return this;
      }
      int[] updatedIds = ArrayUtil.append(ids, id);
      return new ChildrenIds(updatedIds, stillSorted, areAllChildrenLoaded());
    }

    public @NotNull ChildrenIds removeAt(int index) {
      if (isInvalidated()) {
        return this;
      }
      int[] updatedIds = ArrayUtil.remove(ids, index);
      return withIds(updatedIds);
    }

    public @NotNull ChildrenIds removeIds(@NotNull IntSet idsToRemove) {
      if (isInvalidated()) {
        return this;
      }
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
      if (isInvalidated()) {
        return "Children[INVALIDATED]";
      }
      return "Children[ids: " + Arrays.toString(ids) + ", sortedByName: " + isSorted() + ", allLoaded: " + areAllChildrenLoaded() + "]";
    }
  }
}
