// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.LowMemoryWatcher;
import com.intellij.openapi.util.ThreadLocalCachedValue;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.*;
import com.intellij.util.containers.LimitedPool;
import com.intellij.util.containers.SLRUCache;
import org.jetbrains.annotations.*;

import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Stream;

/** Thread-safe implementation of persistent hash map (PHM). The implementation works in the following (generic) way:<ul>
 <li> Particular key is translated via myEnumerator into an int. </li>
 <li> As part of enumeration process for the new key, additional space is reserved in
 myEnumerator.myStorage for offset in ".values" file (myValueStorage) where (serialized) value is stored. </li>
 <li> Once new value is written the offset storage is updated. </li>
 <li> When the key is removed from PHM, offset storage is set to zero. </li>
 </ul>
<p>
 It is important to note that offset
 is non-negative and can be 4 or 8 bytes, depending on the size of the ".values" file.
 PHM can work in appendable mode: for particular key additional calculated chunk of value can be appended to ".values" file with the offset
 of previously calculated chunk.
 For performance reasons we try hard to minimize storage occupied by keys / offsets in ".values" file: this storage is allocated as (limited)
 direct byte buffers so 4 bytes offset is used until it is possible. Generic record produced by enumerator used with PHM as part of new
 key enumeration is <enumerated_id>? [.values file offset 4 or 8 bytes], however for unique integral keys enumerate_id isn't produced.
 Also for certain Value types it is possible to avoid random reads at all: e.g. in case Value is non-negative integer the value can be stored
 directly in storage used for offset and in case of btree enumerator directly in btree leaf.
 **/
public class PersistentMapImpl<Key, Value> implements PersistentMapBase<Key, Value> {
  private static final Logger LOG = Logger.getInstance(PersistentMapImpl.class);
  private static final boolean myDoTrace = SystemProperties.getBooleanProperty("idea.trace.persistent.map", false);
  private static final int DEAD_KEY_NUMBER_MASK = 0xFFFFFFFF;

  private final Path myStorageFile;
  private final boolean myIsReadOnly;
  private final KeyDescriptor<Key> myKeyDescriptor;

  private PersistentHashMapValueStorage myValueStorage;
  private final SLRUCache<Key, BufferExposingByteArrayOutputStream> myAppendCache;
  private final LowMemoryWatcher myAppendCacheFlusher;

  private final DataExternalizer<Value> myValueExternalizer;
  private static final long NULL_ADDR = 0;
  private static final int INITIAL_INDEX_SIZE;

  static {
    String property = System.getProperty("idea.initialIndexSize");
    INITIAL_INDEX_SIZE = property == null ? 4 * 1024 : Integer.parseInt(property);
  }

  @NonNls
  static final String DATA_FILE_EXTENSION = PersistentHashMap.DATA_FILE_EXTENSION;
  private long myLiveAndGarbageKeysCounter;
  // first four bytes contain live keys count (updated via LIVE_KEY_MASK), last four bytes - number of dead keys
  private int myReadCompactionGarbageSize;
  private static final long LIVE_KEY_MASK = 1L << 32;
  private static final long USED_LONG_VALUE_MASK = 1L << 62;
  private static final int POSITIVE_VALUE_SHIFT = 1;
  private final int myParentValueRefOffset;
  private final boolean myIntMapping;
  private final boolean myDirectlyStoreLongFileOffsetMode;
  private final boolean myCanReEnumerate;
  private int myLargeIndexWatermarkId;  // starting with this id we store offset in adjacent file in long format
  private boolean myIntAddressForNewRecord;
  private static final boolean doHardConsistencyChecks = false;
  private final PersistentEnumeratorBase<Key> myEnumerator;
  private final boolean myCompactOnClose;
  private final ReentrantReadWriteLock myLock = new ReentrantReadWriteLock();
  private final PersistentMapWal<Key, Value> myWal;

  @TestOnly
  public boolean isCorrupted() {
    // please do not use this method outside of tests (e.g. as in Scala plugin)
    return myEnumerator.isCorrupted();
  }

  private static final class AppendStream extends DataOutputStream {
    private AppendStream() {
      super(null);
    }

    private void setOut(BufferExposingByteArrayOutputStream stream) {
      out = stream;
    }
  }

  private final LimitedPool<BufferExposingByteArrayOutputStream> myStreamPool =
    new LimitedPool<>(10, new LimitedPool.ObjectFactory<BufferExposingByteArrayOutputStream>() {
      @Override
      @NotNull
      public BufferExposingByteArrayOutputStream create() {
        return new BufferExposingByteArrayOutputStream();
      }

      @Override
      public void cleanup(@NotNull final BufferExposingByteArrayOutputStream appendStream) {
        appendStream.reset();
      }
    });

  private boolean canUseIntAddressForNewRecord(long size) {
    return myCanReEnumerate && size + POSITIVE_VALUE_SHIFT < Integer.MAX_VALUE;
  }

  public PersistentMapImpl(@NotNull PersistentMapBuilder<Key, Value> builder) throws IOException {
    @NotNull Path file = builder.getFile();
    @NotNull KeyDescriptor<Key> keyDescriptor = builder.getKeyDescriptor();
    @NotNull DataExternalizer<Value> valueExternalizer = builder.getValueExternalizer();
    final int initialSize = builder.getInitialSize(INITIAL_INDEX_SIZE);
    int version = builder.getVersion(0);
    @Nullable StorageLockContext lockContext = builder.getLockContext();
    myCompactOnClose = builder.getCompactOnClose(false);
    @NotNull PersistentHashMapValueStorage.CreationTimeOptions options = PersistentHashMapValueStorage.CreationTimeOptions.threadLocalOptions();

    // it's important to initialize it as early as possible
    myIsReadOnly = builder.getReadOnly(false);
    if (myIsReadOnly) options = options.setReadOnly();

    myEnumerator = PersistentEnumerator.createDefaultEnumerator(checkDataFiles(file),
                                                                keyDescriptor,
                                                                initialSize,
                                                                lockContext,
                                                                modifyVersionDependingOnOptions(version, options));

    myStorageFile = file;
    myKeyDescriptor = keyDescriptor;

    Path walFile = file.resolveSibling(file.getFileName().toString() + ".wal");
    myWal = builder.isEnableWal() ? new PersistentMapWal<>(keyDescriptor, valueExternalizer, options.useCompression(), walFile, builder.getWalExecutor(), true) : null;

    final PersistentEnumeratorBase.@NotNull RecordBufferHandler<PersistentEnumeratorBase<?>> recordHandler = myEnumerator.getRecordHandler();
    myParentValueRefOffset = recordHandler.getRecordBuffer(myEnumerator).length;
    myIntMapping = valueExternalizer instanceof IntInlineKeyDescriptor && builder.getInlineValues(false);
    myDirectlyStoreLongFileOffsetMode = keyDescriptor instanceof InlineKeyDescriptor && myEnumerator instanceof PersistentBTreeEnumerator;

    myEnumerator.setRecordHandler(new MyEnumeratorRecordHandler(recordHandler));
    myEnumerator.setMarkCleanCallback(() -> {
      myEnumerator.putMetaData(myLiveAndGarbageKeysCounter);
      myEnumerator.putMetaData2(myLargeIndexWatermarkId | ((long)myReadCompactionGarbageSize << 32));
    });

    if (myDoTrace) LOG.info("Opened " + file);
    try {
      myValueExternalizer = valueExternalizer;
      myValueStorage = myIntMapping ? null : new PersistentHashMapValueStorage(getDataFile(file), options);
      myAppendCache = myIntMapping ? null : createAppendCache(keyDescriptor);
      myAppendCacheFlusher = myIntMapping ? null : LowMemoryWatcher.register(() -> {
        try {
          force();
        }
        catch (IOException e) {
          LOG.error(e);
        }
      });
      myLiveAndGarbageKeysCounter = myEnumerator.getMetaData();
      long data2 = myEnumerator.getMetaData2();
      myLargeIndexWatermarkId = (int)(data2 & DEAD_KEY_NUMBER_MASK);
      myReadCompactionGarbageSize = (int)(data2 >>> 32);
      myCanReEnumerate = myEnumerator.canReEnumerate();

      if (!options.isReadOnly() && makesSenseToCompact()) {
        compact();
      }
    }
    catch (IOException e) {
      try {
        // attempt to close already opened resources
        close(true);
      }
      catch (Throwable ignored) {
      }
      throw e; // rethrow
    }
    catch (Throwable t) {
      LOG.error(t);
      try {
        // attempt to close already opened resources
        close(true);
      }
      catch (Throwable ignored) {
      }
      throw new CorruptedException(file);
    }
  }

  @Override
  public @NotNull DataExternalizer<Value> getValuesExternalizer() {
    return myValueExternalizer;
  }

  private static int modifyVersionDependingOnOptions(int version, @NotNull PersistentHashMapValueStorage.CreationTimeOptions options) {
    return version + options.getVersion();
  }

  private static final int MAX_RECYCLED_BUFFER_SIZE = 4096;

  private SLRUCache<Key, BufferExposingByteArrayOutputStream> createAppendCache(final KeyDescriptor<Key> keyDescriptor) {
    return new SLRUCache<Key, BufferExposingByteArrayOutputStream>(16 * 1024, 4 * 1024, keyDescriptor) {
      @Override
      @NotNull
      public BufferExposingByteArrayOutputStream createValue(final Key key) {
        return myStreamPool.alloc();
      }

      @Override
      protected void onDropFromCache(final Key key, @NotNull final BufferExposingByteArrayOutputStream bytes) {
        myEnumerator.lockStorageWrite();
        try {
          long previousRecord;
          final int id;
          if (myDirectlyStoreLongFileOffsetMode) {
            previousRecord = ((PersistentBTreeEnumerator<Key>)myEnumerator).getNonNegativeValue(key);
            id = -1;
          }
          else {
            id = enumerate(key);
            previousRecord = readValueId(id);
          }

          long headerRecord = myValueStorage.appendBytes(bytes.toByteArraySequence(), previousRecord);

          if (myDirectlyStoreLongFileOffsetMode) {
            ((PersistentBTreeEnumerator<Key>)myEnumerator).putNonNegativeValue(key, headerRecord);
          }
          else {
            updateValueId(id, headerRecord, previousRecord, key, 0);
          }

          if (previousRecord == NULL_ADDR) {
            myLiveAndGarbageKeysCounter += LIVE_KEY_MASK;
          }

          if (bytes.getInternalBuffer().length <= MAX_RECYCLED_BUFFER_SIZE) {
            // Avoid internal fragmentation by not retaining / reusing large append buffers (IDEA-208533)
            myStreamPool.recycle(bytes);
          }
        }
        catch (IOException e) {
          markCorrupted();
          throw new RuntimeException(e);
        }
        finally {
          myEnumerator.unlockStorageWrite();
        }
      }
    };
  }

  @NotNull
  protected Lock getWriteLock() {
    return myLock.writeLock();
  }

  @NotNull
  protected Lock getReadLock() {
    return PersistentEnumeratorBase.USE_RW_LOCK ? myLock.readLock() : myLock.writeLock();
  }

  private static boolean doNewCompact() {
    return System.getProperty("idea.persistent.hash.map.oldcompact") == null;
  }

  private boolean forceNewCompact() {
    return System.getProperty("idea.persistent.hash.map.newcompact") != null &&
           (int)(myLiveAndGarbageKeysCounter & DEAD_KEY_NUMBER_MASK) > 0;
  }

  public int getSize() {
    return (int)(myLiveAndGarbageKeysCounter / LIVE_KEY_MASK);
  }

  public int getGarbageSize() {
    return (int)myLiveAndGarbageKeysCounter;
  }

  public Path getBaseFile() {
    return myEnumerator.myFile;
  }

  @Override
  public void closeAndDelete() {
    Path baseFile = getBaseFile();
    try {
      this.close(true);
    }
    catch (IOException ignored) {}
    IOUtil.deleteAllFilesStartingWith(baseFile.toFile());
    try {
      if (myWal != null) {
        myWal.closeAndDelete();
      }
    }
    catch (IOException ignored) {}
  }

  @TestOnly // public for tests
  @SuppressWarnings("WeakerAccess") // used in upsource for some reason
  public boolean makesSenseToCompact() {
    if (!isCompactionSupported()) return false;

    final long fileSize = myValueStorage.getSize();
    final int megabyte = 1024 * 1024;

    if (fileSize > 5 * megabyte) { // file is longer than 5MB and (more than 50% of keys is garbage or approximate benefit larger than 100M)
      int liveKeys = (int)(myLiveAndGarbageKeysCounter / LIVE_KEY_MASK);
      int deadKeys = (int)(myLiveAndGarbageKeysCounter & DEAD_KEY_NUMBER_MASK);

      if (fileSize > 50 * megabyte && forceNewCompact()) return true;
      if (deadKeys < 50) return false;

      final long benefitSize = Math.max(100 * megabyte, fileSize / 4);
      final long avgValueSize = fileSize / (liveKeys + deadKeys);

      return deadKeys > liveKeys ||
             avgValueSize * deadKeys > benefitSize ||
             myReadCompactionGarbageSize > fileSize / 2;
    }
    return false;
  }

  @NotNull
  private static Path checkDataFiles(@NotNull Path file) {
    if (!Files.exists(file)) {
      IOUtil.deleteAllFilesStartingWith(getDataFile(file).toFile());
    }
    return file;
  }

  @NotNull
  static Path getDataFile(@NotNull Path file) { // made public for testing
    return file.resolveSibling(file.getFileName() + DATA_FILE_EXTENSION);
  }

  @Override
  public final void put(Key key, Value value) throws IOException {
    if (myIsReadOnly) throw new IncorrectOperationException();
    if (myWal != null) {
      myWal.put(key, value);
    }

    getWriteLock().lock();
    try {
      doPut(key, value);
    }
    catch (IOException ex) {
      markCorrupted();
      throw ex;
    }
    finally {
      getWriteLock().unlock();
    }
  }

  protected void doPut(Key key, Value value) throws IOException {
    long newValueOffset = -1;

    if (!myIntMapping) {
      final BufferExposingByteArrayOutputStream bytes = new BufferExposingByteArrayOutputStream();
      AppendStream appenderStream = ourFlyweightAppenderStream.getValue();
      appenderStream.setOut(bytes);
      myValueExternalizer.save(appenderStream, value);
      appenderStream.setOut(null);
      newValueOffset = myValueStorage.appendBytes(bytes.toByteArraySequence(), 0);
    }

    myEnumerator.lockStorageWrite();
    try {
      myEnumerator.markDirty(true);
      flushAppendCache(key);

      long oldValueOffset;
      if (myDirectlyStoreLongFileOffsetMode) {
        if (myIntMapping) {
          ((PersistentBTreeEnumerator<Key>)myEnumerator).putNonNegativeValue(key, (Integer)value);
          return;
        }
        oldValueOffset = ((PersistentBTreeEnumerator<Key>)myEnumerator).getNonNegativeValue(key);
        ((PersistentBTreeEnumerator<Key>)myEnumerator).putNonNegativeValue(key, newValueOffset);
      }
      else {
        final int id = enumerate(key);
        if (myIntMapping) {
          myEnumerator.myStorage.putInt(id + myParentValueRefOffset, (Integer)value);
          return;
        }

        oldValueOffset = readValueId(id);
        updateValueId(id, newValueOffset, oldValueOffset, key, 0);
      }

      if (oldValueOffset != NULL_ADDR) {
        myLiveAndGarbageKeysCounter++;
      }
      else {
        myLiveAndGarbageKeysCounter += LIVE_KEY_MASK;
      }
    }
    finally {
      myEnumerator.unlockStorageWrite();
    }
  }

  private int enumerate(Key name) throws IOException {
    if (myIsReadOnly) throw new IncorrectOperationException();
    getWriteLock().lock();
    try {
      myIntAddressForNewRecord = canUseIntAddressForNewRecord(myValueStorage.getSize());
      return myEnumerator.enumerate(name);
    }
    finally {
      getWriteLock().unlock();
    }
  }

  /**
   * Appends value chunk from specified appender to key's value.
   * Important use note: value externalizer used by this map should process all bytes from DataInput during deserialization and make sure
   * that deserialized value is consistent with value chunks appended.
   * E.g. Value can be Set of String and individual Strings can be appended with this method for particular key, when {@link #get(Object)} will
   * be eventually called for the key, deserializer will read all bytes retrieving Strings and collecting them into Set
   */
  @Override
  public final void appendData(Key key, @NotNull AppendablePersistentMap.ValueDataAppender appender) throws IOException {
    if (myIsReadOnly) throw new IncorrectOperationException();
    if (myWal != null) {
      myWal.appendData(key, appender);
    }

    getWriteLock().lock();
    try {
      doAppendData(key, appender);
    }
    catch (IOException ex) {
      markCorrupted();
      throw ex;
    }
    finally {
      getWriteLock().unlock();
    }
  }

  private static final ThreadLocalCachedValue<AppendStream> ourFlyweightAppenderStream = new ThreadLocalCachedValue<AppendStream>() {
    @NotNull
    @Override
    protected AppendStream create() {
      return new AppendStream();
    }
  };

  private void doAppendData(Key key, @NotNull AppendablePersistentMap.ValueDataAppender appender) throws IOException {
    assert !myIntMapping;
    myEnumerator.markDirty(true);

    AppendStream appenderStream = ourFlyweightAppenderStream.getValue();
    BufferExposingByteArrayOutputStream stream = myAppendCache.get(key);
    appenderStream.setOut(stream);
    myValueStorage.checkAppendsAllowed(stream.size());
    appender.append(appenderStream);
    appenderStream.setOut(null);
  }

  /**
   * Process all keys registered in the map. Note that keys which were removed after {@link #compact()} call will be processed as well. Use
   * {@link #processExistingKeys(Processor)} to process only keys with existing mappings
   */
  @Override
  public final boolean processKeys(@NotNull Processor<? super Key> processor) throws IOException {
    getReadLock().lock();
    try {
      flushAppendCache();
      return myEnumerator.iterateData(processor);
    }
    catch (IOException e) {
      markCorrupted();
      throw e;
    }
    finally {
      getReadLock().unlock();
    }
  }

  @Override
  public boolean isClosed() {
    return myEnumerator.isClosed();
  }

  @Override
  public boolean isDirty() {
    return myEnumerator.isDirty();
  }

  @Override
  public void markDirty() throws IOException {
    getWriteLock().lock();
    try {
      myEnumerator.markDirty(true);
    }
    finally {
      getWriteLock().unlock();
    }
  }

  @Override
  public final boolean processExistingKeys(@NotNull Processor<? super Key> processor) throws IOException {
    getReadLock().lock();
    try {
      flushAppendCache();
      return myEnumerator.processAllDataObject(processor, new PersistentEnumeratorBase.DataFilter() {
        @Override
        public boolean accept(final int id) throws IOException {
          return readValueId(id) != NULL_ADDR;
        }
      });
    }
    catch (IOException e) {
      markCorrupted();
      throw e;
    }
    finally {
      getReadLock().unlock();
    }
  }

  @Override
  public final Value get(Key key) throws IOException {
    getReadLock().lock();
    try {
      return doGet(key);
    }
    catch (IOException ex) {
      markCorrupted();
      throw ex;
    }
    finally {
      getReadLock().unlock();
    }
  }

  private void markCorrupted() {
    if (!myStorageFile.getFileSystem().isReadOnly()) {
      try {
        myEnumerator.markCorrupted();
      } catch (Exception e) {
        LOG.warn(e);
      }
    }
  }

  @Nullable
  protected Value doGet(Key key) throws IOException {
    flushAppendCache(key);

    myEnumerator.lockStorageRead();
    final long valueOffset;
    final int id;
    try {
      if (myDirectlyStoreLongFileOffsetMode) {
        valueOffset = ((PersistentBTreeEnumerator<Key>)myEnumerator).getNonNegativeValue(key);
        if (myIntMapping) {
          //noinspection unchecked
          return (Value)(Integer)(int)valueOffset;
        }
        id = -1;
      }
      else {
        id = myEnumerator.tryEnumerate(key);
        if (id == PersistentEnumeratorBase.NULL_ID) {
          return null;
        }

        if (myIntMapping) {
          //noinspection unchecked
          return (Value)(Integer)myEnumerator.myStorage.getInt(id + myParentValueRefOffset);
        }

        valueOffset = readValueId(id);
      }

      if (valueOffset == NULL_ADDR) {
        return null;
      }
    }
    finally {
      myEnumerator.unlockStorageRead();
    }

    final PersistentHashMapValueStorage.ReadResult readResult = myValueStorage.readBytes(valueOffset);

    final Value valueRead;
    try (DataInputStream input = new DataInputStream(new UnsyncByteArrayInputStream(readResult.buffer))) {
      valueRead = myValueExternalizer.read(input);
    }

    if (myValueStorage.performChunksCompaction(readResult.chunksCount)) {
      long newValueOffset = myValueStorage.compactChunks(new AppendablePersistentMap.ValueDataAppender() {
        @Override
        public void append(@NotNull DataOutput out) throws IOException {
          myValueExternalizer.save(out, valueRead);
        }
      }, readResult);

      myEnumerator.lockStorageWrite();
      try {
        myEnumerator.markDirty(true);

        if (myDirectlyStoreLongFileOffsetMode) {
          ((PersistentBTreeEnumerator<Key>)myEnumerator).putNonNegativeValue(key, newValueOffset);
        }
        else {
          updateValueId(id, newValueOffset, valueOffset, key, 0);
        }
        myLiveAndGarbageKeysCounter++;
        myReadCompactionGarbageSize += readResult.buffer.length;
      }
      finally {
        myEnumerator.unlockStorageWrite();
      }
    }
    return valueRead;
  }

  @Override
  public final boolean containsKey(Key key) throws IOException {
    getReadLock().lock();
    try {
      return doContainsMapping(key);
    }
    finally {
      getReadLock().unlock();
    }
  }

  private boolean doContainsMapping(Key key) throws IOException {
    flushAppendCache(key);

    myEnumerator.lockStorageRead();
    try {
      if (myDirectlyStoreLongFileOffsetMode) {
        return ((PersistentBTreeEnumerator<Key>)myEnumerator).getNonNegativeValue(key) != NULL_ADDR;
      }
      else {
        final int id = myEnumerator.tryEnumerate(key);
        if (id == PersistentEnumeratorBase.NULL_ID) {
          return false;
        }
        if (myIntMapping) return true;
        return readValueId(id) != NULL_ADDR;
      }
    }
    finally {
      myEnumerator.unlockStorageRead();
    }
  }

  @Override
  public final void remove(Key key) throws IOException {
    if (myIsReadOnly) throw new IncorrectOperationException();
    if (myWal != null) {
      myWal.remove(key);
    }

    getWriteLock().lock();
    try {
      doRemove(key);
    }
    finally {
      getWriteLock().unlock();
    }
  }

  protected void doRemove(Key key) throws IOException {
    myEnumerator.lockStorageWrite();
    try {
      flushAppendCache(key);
      final long record;
      if (myDirectlyStoreLongFileOffsetMode) {
        assert !myIntMapping; // removal isn't supported
        record = ((PersistentBTreeEnumerator<Key>)myEnumerator).getNonNegativeValue(key);
        if (record != NULL_ADDR) {
          ((PersistentBTreeEnumerator<Key>)myEnumerator).putNonNegativeValue(key, NULL_ADDR);
        }
      }
      else {
        final int id = myEnumerator.tryEnumerate(key);
        if (id == PersistentEnumeratorBase.NULL_ID) {
          return;
        }
        assert !myIntMapping; // removal isn't supported
        myEnumerator.markDirty(true);

        record = readValueId(id);
        updateValueId(id, NULL_ADDR, record, key, 0);
      }
      if (record != NULL_ADDR) {
        myLiveAndGarbageKeysCounter++;
        myLiveAndGarbageKeysCounter -= LIVE_KEY_MASK;
      }
    }
    finally {
      myEnumerator.unlockStorageWrite();
    }
  }

  @Override
  public final void force() throws IOException {
    if (myIsReadOnly) return;
    if (myDoTrace) LOG.info("Forcing " + myStorageFile);
    if (myWal != null) {
      myWal.flush();
    }
    getWriteLock().lock();
    try {
      doForce();
    }
    finally {
      getWriteLock().unlock();
    }
  }

  protected void doForce() {
    myEnumerator.lockStorageWrite();
    try {
      try {
        clearAppenderCaches();
      }
      finally {
        myEnumerator.force();
      }
    }
    finally {
      myEnumerator.unlockStorageWrite();
    }
  }

  private void clearAppenderCaches() {
    if (myIntMapping) return;
    flushAppendCache();
    myValueStorage.force();
  }

  @Override
  public final void close() throws IOException {
    close(false);
  }

  private void close(boolean emergency) throws IOException {
    if (myDoTrace) LOG.info("Closed " + myStorageFile);

    getWriteLock().lock();
    try {
      if (isClosed()) return;

      if (myWal != null) {
        myWal.close();
      }

      try {
        if (!emergency && myCompactOnClose && isCompactionSupported()) {
          compact();
        }
      }
      finally {
        doClose();
      }
    }
    finally {
      getWriteLock().unlock();
    }
  }

  private void doClose() throws IOException {
    myEnumerator.lockStorageWrite();
    try {
      try {
        try {
          if (myAppendCacheFlusher != null) {
            myAppendCacheFlusher.stop();
          }
          flushAppendCache();
        }
        catch (RuntimeException ex) {
          Throwable cause = ex.getCause();
          if (cause instanceof IOException) throw (IOException)cause;
          throw ex;
        }
      }
      finally {
        final PersistentHashMapValueStorage valueStorage = myValueStorage;
        try {
          if (valueStorage != null) {
            valueStorage.dispose();
          }
        }
        finally {
          myEnumerator.close();
        }
      }
    }
    finally {
      myEnumerator.unlockStorageWrite();
    }
  }

  static final class CompactionRecordInfo {
    final int key;
    final int address;
    long valueAddress;
    long newValueAddress;
    byte[] value;

    CompactionRecordInfo(int _key, long _valueAddress, int _address) {
      key = _key;
      address = _address;
      valueAddress = _valueAddress;
    }
  }

  // make it visible for tests
  @ApiStatus.Internal
  public void compact() throws IOException {
    if (!isCompactionSupported()) throw new IncorrectOperationException();
    getWriteLock().lock();
    try {
      force();
      LOG.info("Compacting " + myEnumerator.myFile);
      LOG.info("Live keys:" + (int)(myLiveAndGarbageKeysCounter / LIVE_KEY_MASK) +
               ", dead keys:" + (int)(myLiveAndGarbageKeysCounter & DEAD_KEY_NUMBER_MASK) +
               ", read compaction size:" + myReadCompactionGarbageSize);

      final long now = System.currentTimeMillis();

      Path oldDataFile = getDataFile(myEnumerator.myFile);
      final File[] oldFiles = getFilesInDirectoryWithNameStartingWith(oldDataFile);

      final Path newPath = oldDataFile.resolveSibling(oldDataFile.getFileName() + ".new");
      PersistentHashMapValueStorage.CreationTimeOptions options = myValueStorage.getOptions();
      final PersistentHashMapValueStorage newStorage = new PersistentHashMapValueStorage(newPath, options);
      myValueStorage.switchToCompactionMode();
      myEnumerator.markDirty(true);
      long sizeBefore = myValueStorage.getSize();

      myLiveAndGarbageKeysCounter = 0;
      myReadCompactionGarbageSize = 0;

      try {
        if (doNewCompact()) {
          newCompact(newStorage);
        }
        else {
          myEnumerator.traverseAllRecords(new PersistentEnumeratorBase.RecordsProcessor() {
            @Override
            public boolean process(final int keyId) throws IOException {
              final long record = readValueId(keyId);
              if (record != NULL_ADDR) {
                PersistentHashMapValueStorage.ReadResult readResult = myValueStorage.readBytes(record);
                long value = newStorage.appendBytes(readResult.buffer, 0, readResult.buffer.length, 0);
                updateValueId(keyId, value, record, null, getCurrentKey());
                myLiveAndGarbageKeysCounter += LIVE_KEY_MASK;
              }
              return true;
            }
          });
        }
      }
      finally {
        newStorage.dispose();
      }

      myValueStorage.dispose();

      for (File f : oldFiles) {
        assert FileUtil.deleteWithRenaming(f);
      }

      final long newSize = newStorage.getSize();

      final File[] newFiles = getFilesInDirectoryWithNameStartingWith(newPath);

      // newFiles should get the same names as oldDataFiles
      File parentFile = newPath.getParent().toFile();
      final String newBaseName = newPath.getFileName().toString();
      final String oldDataFileBaseName = oldDataFile.getFileName().toString();
      for (File f : newFiles) {
        String nameAfterRename = StringUtil.replace(f.getName(), newBaseName, oldDataFileBaseName);
        FileUtil.rename(f, new File(parentFile, nameAfterRename));
      }

      myValueStorage = new PersistentHashMapValueStorage(oldDataFile, options);
      LOG.info("Compacted " + myEnumerator.myFile + ":" + sizeBefore + " bytes into " +
               newSize + " bytes in " + (System.currentTimeMillis() - now) + "ms.");
      myEnumerator.putMetaData(myLiveAndGarbageKeysCounter);
      myEnumerator.putMetaData2(myLargeIndexWatermarkId);
      if (myDoTrace) LOG.assertTrue(myEnumerator.isDirty());
    }
    finally {
      getWriteLock().unlock();
    }
  }

  @ApiStatus.Internal
  public boolean isCompactionSupported() {
    return !myIsReadOnly && !myIntMapping;
  }

  private void flushAppendCache(Key key) {
    if (myAppendCache != null) {
      myAppendCache.remove(key);
    }
  }

  private void flushAppendCache() {
    if (myAppendCache != null) {
      myAppendCache.clear();
    }
  }

  private static File[] getFilesInDirectoryWithNameStartingWith(@NotNull Path fileFromDirectory) throws IOException {
    Path parentFile = fileFromDirectory.getParent();
    if (parentFile == null) return ArrayUtil.EMPTY_FILE_ARRAY;
    Path fileName = fileFromDirectory.getFileName();
    try (Stream<Path> children = Files.list(parentFile)) {
      return children.filter(p -> {
        return p.getFileName().toString().startsWith(fileName.toString());
      }).map(p -> p.toFile()).toArray(File[]::new);
    }
  }

  private void newCompact(@NotNull PersistentHashMapValueStorage newStorage) throws IOException {
    long started = System.currentTimeMillis();
    final List<CompactionRecordInfo> infos = new ArrayList<>(10000);

    myEnumerator.traverseAllRecords(new PersistentEnumeratorBase.RecordsProcessor() {
      @Override
      public boolean process(final int keyId) throws IOException {
        final long record = readValueId(keyId);
        if (record != NULL_ADDR) {
          infos.add(new CompactionRecordInfo(getCurrentKey(), record, keyId));
        }
        return true;
      }
    });

    LOG.info("Loaded mappings:" + (System.currentTimeMillis() - started) + "ms, keys:" + infos.size());
    started = System.currentTimeMillis();
    long fragments = 0;
    if (!infos.isEmpty()) {
      try {
        fragments = myValueStorage.compactValues(infos, newStorage);
      }
      catch (IOException e) {
        throw e;
      }
      catch (Throwable t) {
        throw new IOException("Compaction failed", t);
      }
    }

    LOG.info("Compacted values for:" + (System.currentTimeMillis() - started) + "ms fragments:" +
             (int)fragments + ", new fragments:" + (fragments >> 32));

    started = System.currentTimeMillis();
    try {
      myEnumerator.lockStorageWrite();

      for (CompactionRecordInfo info : infos) {
        updateValueId(info.address, info.newValueAddress, info.valueAddress, null, info.key);
        myLiveAndGarbageKeysCounter += LIVE_KEY_MASK;
      }
    }
    finally {
      myEnumerator.unlockStorageWrite();
    }
    LOG.info("Updated mappings:" + (System.currentTimeMillis() - started) + " ms");
  }

  private long readValueId(final int keyId) throws IOException {
    if (myDirectlyStoreLongFileOffsetMode) {
      return ((PersistentBTreeEnumerator<Key>)myEnumerator).keyIdToNonNegativeOffset(keyId);
    }
    long address = myEnumerator.myStorage.getInt(keyId + myParentValueRefOffset);
    if (address == 0 || address == -POSITIVE_VALUE_SHIFT) {
      return NULL_ADDR;
    }

    if (address < 0) {
      address = -address - POSITIVE_VALUE_SHIFT;
    }
    else {
      long value = myEnumerator.myStorage.getInt(keyId + myParentValueRefOffset + 4) & 0xFFFFFFFFL;
      address = ((address << 32) + value) & ~USED_LONG_VALUE_MASK;
    }

    return address;
  }

  private int smallKeys;
  private int largeKeys;
  private int transformedKeys;
  private int requests;

  private void updateValueId(int keyId, long value, long oldValue, @Nullable Key key, int processingKey) throws IOException {
    if (myDirectlyStoreLongFileOffsetMode) {
      ((PersistentBTreeEnumerator<Key>)myEnumerator).putNonNegativeValue(((InlineKeyDescriptor<Key>)myKeyDescriptor).fromInt(processingKey), value);
      return;
    }
    final boolean newKey = oldValue == NULL_ADDR;
    if (newKey) ++requests;
    boolean defaultSizeInfo = true;

    if (myCanReEnumerate) {
      if (canUseIntAddressForNewRecord(value)) {
        defaultSizeInfo = false;
        myEnumerator.myStorage.putInt(keyId + myParentValueRefOffset, -(int)(value + POSITIVE_VALUE_SHIFT));
        if (newKey) ++smallKeys;
      }
      else if ((keyId < myLargeIndexWatermarkId || myLargeIndexWatermarkId == 0) && (newKey || canUseIntAddressForNewRecord(oldValue))) {
        // keyId is result of enumerate, if we do re-enumerate then it is no longer accessible unless somebody cached it
        myIntAddressForNewRecord = false;
        keyId = myEnumerator.reEnumerate(key == null ? myEnumerator.getValue(keyId, processingKey) : key);
        ++transformedKeys;
        if (myLargeIndexWatermarkId == 0) {
          myLargeIndexWatermarkId = keyId;
        }
      }
    }

    if (defaultSizeInfo) {
      value |= USED_LONG_VALUE_MASK;

      myEnumerator.myStorage.putInt(keyId + myParentValueRefOffset, (int)(value >>> 32));
      myEnumerator.myStorage.putInt(keyId + myParentValueRefOffset + 4, (int)value);

      if (newKey) ++largeKeys;
    }

    if (newKey && IOStatistics.DEBUG && (requests & IOStatistics.KEYS_FACTOR_MASK) == 0) {
      IOStatistics.dump("small:" + smallKeys + ", large:" + largeKeys + ", transformed:" + transformedKeys +
                        ",@" + getBaseFile());
    }
    if (doHardConsistencyChecks) {
      long checkRecord = readValueId(keyId);
      assert checkRecord == (value & ~USED_LONG_VALUE_MASK) : value;
    }
  }

  @Override
  public String toString() {
    return getClass().getName() + "@" + Integer.toHexString(hashCode()) + ": " + myStorageFile;
  }

  @TestOnly
  public PersistentHashMapValueStorage getValueStorage() {
    return myValueStorage;
  }

  @TestOnly
  public boolean getReadOnly() {
    return myIsReadOnly;
  }

  @NotNull
  @TestOnly
  public static <Key, Value> PersistentMapImpl<Key, Value> unwrap(@NotNull PersistentHashMap<Key, Value> map) {
    //NOTE: on production, it can be another implementation behind the PersistentHashMap
    try {
      Field field = PersistentHashMap.class.getDeclaredField("myImpl");
      field.setAccessible(true);
      //noinspection unchecked
      return (PersistentMapImpl<Key, Value>)field.get(map);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private class MyEnumeratorRecordHandler extends PersistentEnumeratorBase.RecordBufferHandler<PersistentEnumeratorBase<?>> {
    private final ThreadLocal<byte @NotNull []> myRecordBuffer;
    private final ThreadLocal<byte @NotNull []> mySmallRecordBuffer;
    private final PersistentEnumeratorBase.@NotNull RecordBufferHandler<PersistentEnumeratorBase<?>> myRecordHandler;

    MyEnumeratorRecordHandler(PersistentEnumeratorBase.@NotNull RecordBufferHandler<PersistentEnumeratorBase<?>> recordHandler) {
      myRecordHandler = recordHandler;
      myRecordBuffer = ThreadLocal
        .withInitial(() -> myDirectlyStoreLongFileOffsetMode ? ArrayUtilRt.EMPTY_BYTE_ARRAY : new byte[myParentValueRefOffset + 8]);
      mySmallRecordBuffer = ThreadLocal
        .withInitial(() -> myDirectlyStoreLongFileOffsetMode ? ArrayUtilRt.EMPTY_BYTE_ARRAY : new byte[myParentValueRefOffset + 4]);
    }

    @Override
    int recordWriteOffset(PersistentEnumeratorBase<?> enumerator, byte[] buf) throws IOException {
      return myRecordHandler.recordWriteOffset(enumerator, buf);
    }

    @Override
    byte @NotNull [] getRecordBuffer(PersistentEnumeratorBase<?> enumerator) {
      return myIntAddressForNewRecord ? mySmallRecordBuffer.get() : myRecordBuffer.get();
    }

    @Override
    void setupRecord(PersistentEnumeratorBase enumerator, int hashCode, int dataOffset, byte @NotNull [] buf) {
      myRecordHandler.setupRecord(enumerator, hashCode, dataOffset, buf);
      for (int i = myParentValueRefOffset; i < buf.length; i++) {
        buf[i] = 0;
      }
    }
  }
}
