// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.impl;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.openapi.application.ApplicationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.InvalidVirtualFileAccessException;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl;
import com.intellij.testFramework.TestModeFlags;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.BitUtil;
import com.intellij.util.Functions;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.AtomicFieldUpdater;
import com.intellij.util.containers.*;
import com.intellij.util.keyFMap.KeyFMap;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * The place where all the data is stored for VFS parts loaded into a memory: name-ids, flags, user data, children.
 *
 * The purpose is to avoid holding this data in separate immortal file/directory objects because that involves space overhead, significant
 * when there are hundreds of thousands of files.
 *
 * The data is stored per-id in blocks of {@link #SEGMENT_SIZE}. File ids in one project tend to cluster together,
 * so the overhead for non-loaded id should not be large in most cases.
 *
 * File objects are still created if needed. There might be several objects for the same file, so equals() should be used instead of ==.
 *
 * The lifecycle of a file object is as follows:
 *
 * 1. The file has not been instantiated yet, so {@link #getFileById} returns null.
 *
 * 2. A file is explicitly requested by calling getChildren or findChild on its parent. The parent initializes all the necessary data (in a thread-safe context)
 * and creates the file instance. See {@link #initFile}
 *
 * 3. After that the file is live, an object representing it can be retrieved any time from its parent. File system roots are
 * kept on hard references in {@link PersistentFS}
 *
 * 4. If a file is deleted (invalidated), then its data is not needed anymore, and should be removed. But this can only happen after
 * all the listener have been notified about the file deletion and have had their chance to look at the data the last time. See {@link #killInvalidatedFiles()}
 *
 * 5. The file with removed data is marked as "dead" (see {@link #myDeadMarker}), any access to it will throw {@link InvalidVirtualFileAccessException}
 * Dead ids won't be reused in the same session of the IDE.
 */
public final class VfsData {
  @TestOnly
  public static final Key<Boolean> ENABLE_IS_INDEXED_FLAG_KEY = new Key<>("is_indexed_flag_enabled");
  private static final Logger LOG = Logger.getInstance(VfsData.class);
  private static volatile Boolean isIsIndexedFlagDisabled = null;
  private static final int SEGMENT_BITS = 9;
  private static final int SEGMENT_SIZE = 1 << SEGMENT_BITS;
  private static final int OFFSET_MASK = SEGMENT_SIZE - 1;

  private static final short NULL_INDEXING_STAMP = 0;
  private static final AtomicInteger ourIndexingStamp = new AtomicInteger(1);

  public static boolean isIsIndexedFlagDisabled() {
    if (isIsIndexedFlagDisabled == null) {
      Boolean enable;
      if (ApplicationManager.getApplication().isUnitTestMode() && ((enable = TestModeFlags.get(ENABLE_IS_INDEXED_FLAG_KEY)) != null)) {
        isIsIndexedFlagDisabled = !enable;
      }
      else {
        isIsIndexedFlagDisabled = Registry.is("indexing.disable.virtual.file.system.entry.is.file.indexed", true);
      }
    }
    return isIsIndexedFlagDisabled;
  }

  @ApiStatus.Internal
  static void markAllFilesAsUnindexed() {
    ourIndexingStamp.updateAndGet(cur -> {
      int next = cur + 1;
      return (short)next == NULL_INDEXING_STAMP ? (NULL_INDEXING_STAMP + 1) : next;
    });
  }

  private final Object myDeadMarker = ObjectUtils.sentinel("dead file");

  private final ConcurrentIntObjectMap<Segment> mySegments = ConcurrentCollectionFactory.createConcurrentIntObjectMap();
  private final ConcurrentBitSet myInvalidatedIds = ConcurrentBitSet.create();

  /** guarded by {@link #myDeadMarker} */
  private IntSet myDyingIds = new IntOpenHashSet();

  private final IntObjectMap<VirtualDirectoryImpl> myChangedParents = ConcurrentCollectionFactory.createConcurrentIntObjectMap();

  public VfsData() {
    ApplicationManager.getApplication().addApplicationListener(new ApplicationListener() {
      @Override
      public void writeActionFinished(@NotNull Object action) {
        // after top-level write action is finished, all the deletion listeners should have processed the deleted files
        // and their data is considered safe to remove. From this point on accessing a removed file will result in an exception.
        if (!ApplicationManager.getApplication().isWriteAccessAllowed()) {
          killInvalidatedFiles();
        }
      }
    }, ApplicationManager.getApplication());
  }

  private void killInvalidatedFiles() {
    synchronized (myDeadMarker) {
      if (!myDyingIds.isEmpty()) {
        for (IntIterator iterator = myDyingIds.iterator(); iterator.hasNext(); ) {
          int id = iterator.nextInt();
          Segment segment = Objects.requireNonNull(getSegment(id, false));
          segment.myObjectArray.set(getOffset(id), myDeadMarker);
          myChangedParents.remove(id);
        }
        myDyingIds = new IntOpenHashSet();
      }
    }
  }

  @Nullable
  VirtualFileSystemEntry getFileById(int id, @NotNull VirtualDirectoryImpl parent, boolean putToMemoryCache) {
    PersistentFSImpl persistentFS = (PersistentFSImpl)PersistentFS.getInstance();
    VirtualFileSystemEntry dir = persistentFS.getCachedDir(id);
    if (dir != null) return dir;

    Segment segment = getSegment(id, false);
    if (segment == null) return null;

    int offset = getOffset(id);
    Object o = segment.myObjectArray.get(offset);
    if (o == null) return null;

    if (o == myDeadMarker) {
      throw reportDeadFileAccess(new VirtualFileImpl(id, segment, parent));
    }
    final int nameId = segment.getNameId(id);
    if (nameId <= 0) {
      FSRecords.invalidateCaches();
      throw new AssertionError("nameId=" + nameId + "; data=" + o + "; parent=" + parent + "; parent.id=" + parent.getId() + "; db.parent=" + FSRecords.getParent(id));
    }

    if (o instanceof DirectoryData) {
      if (putToMemoryCache) {
        return persistentFS.getOrCacheDir(new VirtualDirectoryImpl(id, segment, (DirectoryData)o, parent, parent.getFileSystem()));
      }
      VirtualFileSystemEntry entry = persistentFS.getCachedDir(id);
      if (entry != null) return entry;
      return new VirtualDirectoryImpl(id, segment, (DirectoryData)o, parent, parent.getFileSystem());
    }
    return new VirtualFileImpl(id, segment, parent);
  }

  private static @NotNull InvalidVirtualFileAccessException reportDeadFileAccess(@NotNull VirtualFileSystemEntry file) {
    return new InvalidVirtualFileAccessException("Accessing dead virtual file: " + file.getUrl());
  }

  private static int getOffset(int id) {
    if (id <= 0) throw new IllegalArgumentException("invalid argument id: "+id);
    return id & OFFSET_MASK;
  }

  @Contract("_,true->!null")
  public Segment getSegment(int id, boolean create) {
    int key = id >>> SEGMENT_BITS;
    Segment segment = mySegments.get(key);
    if (segment != null || !create) {
      return segment;
    }
    return mySegments.cacheOrGet(key, new Segment(this));
  }

  public boolean hasLoadedFile(int id) {
    Segment segment = getSegment(id, false);
    return segment != null && segment.myObjectArray.get(getOffset(id)) != null;
  }

  public static final class FileAlreadyCreatedException extends RuntimeException {
    private FileAlreadyCreatedException(@NotNull String message) {
      super(message);
    }
  }

  static void initFile(int id, @NotNull Segment segment, int nameId, @NotNull Object data) throws FileAlreadyCreatedException {
    int offset = getOffset(id);

    segment.setNameId(id, nameId);

    Object existingData = segment.myObjectArray.get(offset);
    if (existingData != null) {
      String msg = FSRecords.describeAlreadyCreatedFile(id, nameId);
      throw new FileAlreadyCreatedException(msg);
    }
    segment.myObjectArray.set(offset, data);
  }

  @NotNull
  CharSequence getNameByFileId(int id) {
    return FileNameCache.getVFileName(getNameId(id));
  }

  int getNameId(int id) {
    return Objects.requireNonNull(getSegment(id, false)).getNameId(id);
  }

  boolean isFileValid(int id) {
    return !myInvalidatedIds.get(id);
  }

  @Nullable
  VirtualDirectoryImpl getChangedParent(int id) {
    return myChangedParents.get(id);
  }

  private void changeParent(int id, @NotNull VirtualDirectoryImpl parent) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    myChangedParents.put(id, parent);
  }

  void invalidateFile(int id) {
    myInvalidatedIds.set(id);
    synchronized (myDeadMarker) {
      myDyingIds.add(id);
    }
  }

  static final class Segment {
    // user data for files, DirectoryData for folders
    private final AtomicReferenceArray<Object> myObjectArray;

    // <nameId, flags> pairs, "flags" part containing flags per se and modification stamp
    private final AtomicIntegerArray myIntArray;

    @NotNull
    final VfsData vfsData;

    // the reference is synchronized by read-write lock; clients outside read-action deserve to get outdated result
    @Nullable Segment replacement;

    Segment(@NotNull VfsData vfsData) {
      this(vfsData, new AtomicReferenceArray<>(SEGMENT_SIZE), new AtomicIntegerArray(SEGMENT_SIZE * 3));
    }

    private Segment(@NotNull VfsData vfsData,
                    @NotNull AtomicReferenceArray<Object> objectArray,
                    @NotNull AtomicIntegerArray intArray) {
      myObjectArray = objectArray;
      myIntArray = intArray;
      this.vfsData = vfsData;
    }

    boolean isIndexed(int fileId) {
      if (isIsIndexedFlagDisabled()) {
        return false;
      }
      return myIntArray.get(getOffset(fileId) * 3 + 2) == ourIndexingStamp.intValue();
    }

    void setIndexed(int fileId, boolean indexed) {
      if (isIsIndexedFlagDisabled()) {
        return;
      }
      if (fileId <= 0) throw new IllegalArgumentException("invalid arguments id: " + fileId);
      int stamp = indexed ? ourIndexingStamp.intValue() : NULL_INDEXING_STAMP;
      myIntArray.set(getOffset(fileId) * 3 + 2, stamp);
    }

    int getNameId(int fileId) {
      return myIntArray.get(getOffset(fileId) * 3);
    }

    void setNameId(int fileId, int nameId) {
      if (fileId <= 0 || nameId <= 0) throw new IllegalArgumentException("invalid arguments id: " + fileId + "; nameId: " + nameId);
      myIntArray.set(getOffset(fileId) * 3, nameId);
    }

    void setUserMap(int fileId, @NotNull KeyFMap map) {
      myObjectArray.set(getOffset(fileId), map);
    }

    @NotNull
    KeyFMap getUserMap(@NotNull VirtualFileSystemEntry file, int id) {
      Object o = myObjectArray.get(getOffset(id));
      if (!(o instanceof KeyFMap)) {
        throw reportDeadFileAccess(file);
      }
      return (KeyFMap)o;
    }

    boolean changeUserMap(int fileId, KeyFMap oldMap, KeyFMap newMap) {
      return myObjectArray.compareAndSet(getOffset(fileId), oldMap, newMap);
    }

    boolean getFlag(int id, @VirtualFileSystemEntry.Flags int mask) {
      BitUtil.assertOneBitMask(mask);
      assert (mask & ~VirtualFileSystemEntry.ALL_FLAGS_MASK) == 0 : "Unexpected flag";
      return (myIntArray.get(getOffset(id) * 3 + 1) & mask) != 0;
    }

    void setFlag(int id, @VirtualFileSystemEntry.Flags int mask, boolean value) {
      if (LOG.isTraceEnabled()) {
        LOG.trace("Set flag " + Integer.toHexString(mask) + "=" + value + " for id=" + id);
      }
      assert (mask & ~VirtualFileSystemEntry.ALL_FLAGS_MASK) == 0 : "Unexpected flag";
      int offset = getOffset(id) * 3 + 1;
      myIntArray.updateAndGet(offset, oldInt -> BitUtil.set(oldInt, mask, value));
    }
    void setFlags(int id, @VirtualFileSystemEntry.Flags int combinedMask, @VirtualFileSystemEntry.Flags int combinedValue) {
      if (LOG.isTraceEnabled()) {
        LOG.trace("Set flags " + Integer.toHexString(combinedMask) + "=" + combinedValue + " for id=" + id);
      }
      assert (combinedMask & ~VirtualFileSystemEntry.ALL_FLAGS_MASK) == 0 : "Unexpected flag";
      assert (~combinedMask & combinedValue) == 0 : "Value (" + Integer.toHexString(combinedValue)+ ") set bits outside mask ("+
                                                    Integer.toHexString(combinedMask)+")";
      int offset = getOffset(id) * 3 + 1;
      myIntArray.updateAndGet(offset, oldInt -> oldInt & ~combinedMask | combinedValue);
    }

    long getModificationStamp(int id) {
      return myIntArray.get(getOffset(id) * 3 + 1) & ~VirtualFileSystemEntry.ALL_FLAGS_MASK;
    }

    void setModificationStamp(int id, long stamp) {
      int offset = getOffset(id) * 3 + 1;
      myIntArray.updateAndGet(offset, oldInt -> (oldInt & VirtualFileSystemEntry.ALL_FLAGS_MASK) | ((int)stamp & ~VirtualFileSystemEntry.ALL_FLAGS_MASK));
    }

    void changeParent(int fileId, VirtualDirectoryImpl directory) {
      assert replacement == null;
      replacement = new Segment(vfsData, myObjectArray, myIntArray);
      int key = fileId >>> SEGMENT_BITS;
      boolean replaced = vfsData.mySegments.replace(key, this, replacement);
      assert replaced;
      vfsData.changeParent(fileId, directory);
    }
  }

  // non-final field accesses are synchronized on this instance, but this happens in VirtualDirectoryImpl
  static final class DirectoryData {
    private static final AtomicFieldUpdater<DirectoryData, KeyFMap>
      MY_USER_MAP_UPDATER = AtomicFieldUpdater.forFieldOfType(DirectoryData.class, KeyFMap.class);
    @NotNull
    volatile KeyFMap myUserMap = KeyFMap.EMPTY_MAP;
    /**
     * sorted by {@link VfsData#getNameByFileId(int)}
     * assigned under lock(this) only; never modified in-place
     * @see VirtualDirectoryImpl#findIndex(int[], CharSequence, boolean)
     */
    volatile int @NotNull [] myChildrenIds = ArrayUtilRt.EMPTY_INT_ARRAY; // guarded by this
    volatile boolean myAllChildrenLoaded;

    // assigned under lock(this) only; accessed/modified map contents under lock(myAdoptedNames)
    private volatile Set<CharSequence> myAdoptedNames;

    VirtualFileSystemEntry @NotNull [] getFileChildren(@NotNull VirtualDirectoryImpl parent, boolean putToMemoryCache) {
      int[] ids = myChildrenIds;
      VirtualFileSystemEntry[] children = new VirtualFileSystemEntry[ids.length];
      for (int i = 0; i < ids.length; i++) {
        int childId = ids[i];
        VirtualFileSystemEntry child = parent.getVfsData().getFileById(childId, parent, putToMemoryCache);
        if (child == null) {
          throw new AssertionError("No file for id " + childId + ", parentId = " + parent.myId);
        }
        children[i] = child;
      }
      return children;
    }

    boolean allChildrenLoaded() {
      return myAllChildrenLoaded;
    }
    void setAllChildrenLoaded() {
      myAllChildrenLoaded = true;
    }

    boolean changeUserMap(@NotNull KeyFMap oldMap, @NotNull KeyFMap newMap) {
      return MY_USER_MAP_UPDATER.compareAndSet(this, oldMap, newMap);
    }

    boolean isAdoptedName(@NotNull CharSequence name) {
      Set<CharSequence> adopted = myAdoptedNames;
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
     *
     * Must be called in synchronized(VfsData)
     */
    void removeAdoptedName(@NotNull CharSequence name) {
      Set<CharSequence> adopted = myAdoptedNames;
      if (adopted == null) {
        return;
      }
      synchronized (adopted) {
        boolean removed = adopted.remove(name);
        if (removed && adopted.isEmpty()) {
          myAdoptedNames = null;
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
    @NotNull
    private Set<CharSequence> getOrCreateAdoptedNames(boolean caseSensitive) {
      Set<CharSequence> adopted = myAdoptedNames;
      if (adopted == null) {
        adopted = CollectionFactory.createCharSequenceSet(caseSensitive);
        myAdoptedNames = adopted;
      }
      return adopted;
    }

    @NotNull
    List<String> getAdoptedNames() {
      Set<CharSequence> adopted = myAdoptedNames;
      if (adopted == null) return Collections.emptyList();
      synchronized (adopted) {
        return ContainerUtil.map(adopted, Functions.TO_STRING());
      }
    }

    /**
     * Must be called in synchronized(VfsData)
     */
    void clearAdoptedNames() {
      myAdoptedNames = null;
    }

    @Override
    @NonNls
    public String toString() {
      return "DirectoryData{" +
             "myUserMap=" + myUserMap +
             ", myChildrenIds=" + Arrays.toString(myChildrenIds) +
             ", myAdoptedNames=" + myAdoptedNames +
             '}';
    }
  }
}
