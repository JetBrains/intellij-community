/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.LowMemoryWatcher;
import com.intellij.openapi.util.ThreadLocalCachedValue;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.LimitedPool;
import com.intellij.util.containers.SLRUCache;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntProcedure;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 18, 2007
 */
public class PersistentHashMap<Key, Value> extends PersistentEnumeratorDelegate<Key> implements PersistentMap<Key, Value> {
  // PersistentHashMap (PHM) works in following (generic) way:
  // particular key is translated via myEnumerator into int, as part of enumeration process for new key additional space is reserved in
  // myEnumerator.myStorage for offset in .values file (myValueStorage) where (serialized) value is stored. Once new value is written
  // the offset storage is updated. When the key is removed from PHM, offset storage is set to zero. It is important to note that offset
  // is nonnegative and can be 4 or 8 bytes, depending on size of .values file.
  // PHM can work in appendable mode: for particular key additional calculated chunk of value can be appended to .values file with offset
  // of previously calculated chunk.
  // For performance reasons we try hard to minimize storage occupied by keys / offsets to .values file: this storage is allocated as (limited)
  // direct bytebuffers so 4 bytes offset is used until it is possible. Generic record produced by enumerator used with PHM as part of new
  // key enumeration is <enumerated_id>? [.values file offset 4 or 8 bytes], however for unique integral keys enumerate_id isn't produced.
  // Also for certain Value types it is possible to avoid random reads at all: e.g. in case Value is nonnegative integer the value can be stored
  // directly in storage used for offset and in case of btreeenumerator directly in btree leaf.
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.io.PersistentHashMap");
  private static final boolean myDoTrace = SystemProperties.getBooleanProperty("idea.trace.persistent.map", false);
  private static final int DEAD_KEY_NUMBER_MASK = 0xFFFFFFFF;

  private final File myStorageFile;
  private final boolean myIsReadOnly;
  private final KeyDescriptor<Key> myKeyDescriptor;
  private PersistentHashMapValueStorage myValueStorage;
  protected final DataExternalizer<Value> myValueExternalizer;
  private static final long NULL_ADDR = 0;
  private static final int INITIAL_INDEX_SIZE;
  static {
    String property = System.getProperty("idea.initialIndexSize");
    INITIAL_INDEX_SIZE = property == null ? 4 * 1024 : Integer.valueOf(property);
  }

  @NonNls
  static final String DATA_FILE_EXTENSION = ".values";
  private long myLiveAndGarbageKeysCounter; // first four bytes contain live keys count (updated via LIVE_KEY_MASK), last four bytes - number of dead keys
  private int myReadCompactionGarbageSize;
  private static final long LIVE_KEY_MASK = 1L << 32;
  private static final long USED_LONG_VALUE_MASK = 1L << 62;
  private static final int POSITIVE_VALUE_SHIFT = 1;
  private final int myParentValueRefOffset;
  @NotNull private final byte[] myRecordBuffer;
  @NotNull private final byte[] mySmallRecordBuffer;
  private final boolean myIntMapping;
  private final boolean myDirectlyStoreLongFileOffsetMode;
  private final boolean myCanReEnumerate;
  private int myLargeIndexWatermarkId;  // starting with this id we store offset in adjacent file in long format
  private boolean myIntAddressForNewRecord;
  private static final boolean doHardConsistencyChecks = false;
  private volatile boolean myBusyReading;

  public static Set<String> blackList = ContainerUtil.newHashSet("RefQueueIndex.storage");

  public void dump() {
    if (blackList.contains(myStorageFile.getName())) return;
    System.out.println("dumping " + myStorageFile);
    TCPPersistentMap.Connection c = TCPPersistentMap.connection();
    try {
    final BufferExposingByteArrayOutputStream baos = new BufferExposingByteArrayOutputStream();
    final DataOutputStream dos = new DataOutputStream(baos);
      c.put(new Consumer<DataOutputStream>() {
        @Override
        public void consume(final DataOutputStream stream) {
          try {
            stream.writeUTF(myStorageFile.getPath());
            stream.writeByte(TCPPersistentMap.CREATE_MAP);
            stream.writeBoolean(myKeyDescriptor instanceof InlineKeyDescriptor); //TODO
            if (myKeyDescriptor instanceof InlineKeyDescriptor) {
              stream.writeInt(inlineKeysCache.size());
              inlineKeysCache.forEach(new TIntProcedure() {
                @Override
                public boolean execute(int key) {
                  try {
                    stream.writeInt(key);

                    Value value = get(((InlineKeyDescriptor<Key>)myKeyDescriptor).fromInt(key));
                    myValueExternalizer.save(dos, value);
                    stream.writeInt(baos.size());
                    stream.write(baos.getInternalBuffer(), 0, baos.size());
                    baos.reset();
                  } catch (IOException e) {
                    throw new RuntimeException(e);
                  }
                  return true;
                }
              });
            }
            else {
              processKeys(new Processor<Key>() {
                @Override
                public boolean process(Key key) {
                  try {
                    if (key != null) {
                      myKeyDescriptor.save(dos, key);
                      stream.writeInt(baos.size() + 4);
                      stream.writeInt(myKeyDescriptor.getHashCode(key));
                      stream.write(baos.getInternalBuffer(), 0, baos.size());
                      baos.reset();

                      Value value = get(key);
                      myValueExternalizer.save(dos, value);
                      stream.writeInt(baos.size());
                      stream.write(baos.getInternalBuffer(), 0, baos.size());
                      baos.reset();
                    }
                  }
                  catch (IOException e) {
                    throw new RuntimeException(e);
                  }
                  return true;
                }
              });
              stream.writeInt(0);
            }
          }
          catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
      });
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static class AppendStream extends DataOutputStream {
    private AppendStream() {
      super(null);
    }

    private void setOut(BufferExposingByteArrayOutputStream stream) {
      out = stream;
    }
  }

  private final LimitedPool<BufferExposingByteArrayOutputStream> myStreamPool = new LimitedPool<BufferExposingByteArrayOutputStream>(10, new LimitedPool.ObjectFactory<BufferExposingByteArrayOutputStream>() {
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

  private final SLRUCache<Key, BufferExposingByteArrayOutputStream> myAppendCache;

  private boolean canUseIntAddressForNewRecord(long size) {
    return myCanReEnumerate && size + POSITIVE_VALUE_SHIFT < Integer.MAX_VALUE;
  }

  private final LowMemoryWatcher myAppendCacheFlusher = LowMemoryWatcher.register(new Runnable() {
    @Override
    public void run() {
      dropMemoryCaches();
    }
  });

  public PersistentHashMap(@NotNull final File file, @NotNull KeyDescriptor<Key> keyDescriptor, @NotNull DataExternalizer<Value> valueExternalizer) throws IOException {
    this(file, keyDescriptor, valueExternalizer, INITIAL_INDEX_SIZE);
  }

  public PersistentHashMap(@NotNull final File file, @NotNull KeyDescriptor<Key> keyDescriptor, @NotNull DataExternalizer<Value> valueExternalizer, final int initialSize) throws IOException {
    this(file, keyDescriptor, valueExternalizer, initialSize, 0);
  }

  public PersistentHashMap(@NotNull final File file, @NotNull KeyDescriptor<Key> keyDescriptor, @NotNull DataExternalizer<Value> valueExternalizer, final int initialSize, int version) throws IOException {
    super(checkDataFiles(file), keyDescriptor, initialSize, null, version);

    myStorageFile = file;
    myIsReadOnly = isReadOnly();
    myKeyDescriptor = keyDescriptor;
    myAppendCache = createAppendCache(keyDescriptor);
    final PersistentEnumeratorBase.RecordBufferHandler<PersistentEnumeratorBase> recordHandler = myEnumerator.getRecordHandler();
    myParentValueRefOffset = recordHandler.getRecordBuffer(myEnumerator).length;
    myIntMapping = valueExternalizer instanceof IntInlineKeyDescriptor && wantNonnegativeIntegralValues();
    myDirectlyStoreLongFileOffsetMode = myKeyDescriptor instanceof InlineKeyDescriptor && myEnumerator instanceof PersistentBTreeEnumerator;

    myRecordBuffer = myDirectlyStoreLongFileOffsetMode ? new byte[0]:new byte[myParentValueRefOffset + 8];
    mySmallRecordBuffer = myDirectlyStoreLongFileOffsetMode ? new byte[0]:new byte[myParentValueRefOffset + 4];

    myEnumerator.setRecordHandler(new PersistentEnumeratorBase.RecordBufferHandler<PersistentEnumeratorBase>() {
      @Override
      int recordWriteOffset(PersistentEnumeratorBase enumerator, byte[] buf) {
        return recordHandler.recordWriteOffset(enumerator, buf);
      }

      @NotNull
      @Override
      byte[] getRecordBuffer(PersistentEnumeratorBase enumerator) {
        return myIntAddressForNewRecord ? mySmallRecordBuffer : myRecordBuffer;
      }

      @Override
      void setupRecord(PersistentEnumeratorBase enumerator, int hashCode, int dataOffset, @NotNull byte[] buf) {
        recordHandler.setupRecord(enumerator, hashCode, dataOffset, buf);
        for (int i = myParentValueRefOffset; i < buf.length; i++) {
          buf[i] = 0;
        }
      }
    });

    myEnumerator.setMarkCleanCallback(
      new Flushable() {
        @Override
        public void flush() throws IOException {
          myEnumerator.putMetaData(myLiveAndGarbageKeysCounter);
          myEnumerator.putMetaData2(myLargeIndexWatermarkId | ((long)myReadCompactionGarbageSize << 32));
        }
      }
    );

    if(myDoTrace) LOG.info("Opened " + file);
    try {
      myValueExternalizer = valueExternalizer;
      myValueStorage = PersistentHashMapValueStorage.create(getDataFile(file).getPath(), myIsReadOnly);
      myLiveAndGarbageKeysCounter = myEnumerator.getMetaData();
      long data2 = myEnumerator.getMetaData2();
      myLargeIndexWatermarkId = (int)(data2 & DEAD_KEY_NUMBER_MASK);
      myReadCompactionGarbageSize = (int)(data2 >>> 32);
      myCanReEnumerate = myEnumerator.canReEnumerate();

      if (makesSenseToCompact()) {
        compact();
      }
    }
    catch (IOException e) {
      try {
        // attempt to close already opened resources
        close();
      }
      catch (Throwable ignored) {
      }
      throw e; // rethrow
    }
    catch (Throwable t) {
      LOG.error(t);
      try {
        // attempt to close already opened resources
        close();
      }
      catch (Throwable ignored) {
      }
      throw new PersistentEnumerator.CorruptedException(file);
    }
  }

  protected boolean wantNonnegativeIntegralValues() {
    return false;
  }
  protected boolean isReadOnly() {
    return false;
  }

  private SLRUCache<Key, BufferExposingByteArrayOutputStream> createAppendCache(final KeyDescriptor<Key> keyDescriptor) {
    return new SLRUCache<Key, BufferExposingByteArrayOutputStream>(16 * 1024, 4 * 1024, keyDescriptor) {
      @Override
      @NotNull
      public BufferExposingByteArrayOutputStream createValue(final Key key) {
        return myStreamPool.alloc();
      }

      @Override
      protected void onDropFromCache(final Key key, @NotNull final BufferExposingByteArrayOutputStream bytes) {
        myEnumerator.lockStorage();
        try {
          long previousRecord;
          final int id;
          if (myDirectlyStoreLongFileOffsetMode) {
            previousRecord = ((PersistentBTreeEnumerator<Key>)myEnumerator).getNonnegativeValue(key);
            id = -1;
          } else {
            id = enumerate(key);
            previousRecord = readValueId(id);
          }

          long headerRecord = myValueStorage.appendBytes(bytes.getInternalBuffer(), 0, bytes.size(), previousRecord);

          if (myDirectlyStoreLongFileOffsetMode) {
            ((PersistentBTreeEnumerator<Key>)myEnumerator).putNonnegativeValue(key, headerRecord);
          } else {
            updateValueId(id, headerRecord, previousRecord, key, 0);
          }

          if (previousRecord == NULL_ADDR) {
            myLiveAndGarbageKeysCounter += LIVE_KEY_MASK;
          }

          myStreamPool.recycle(bytes);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
        finally {
          myEnumerator.unlockStorage();
        }
      }
    };
  }

  private boolean doNewCompact() {
    return System.getProperty("idea.persistent.hash.map.oldcompact") == null;
  }

  private boolean forceNewCompact() {
    return System.getProperty("idea.persistent.hash.map.newcompact") != null &&
           ((int)(myLiveAndGarbageKeysCounter & DEAD_KEY_NUMBER_MASK)) > 0;
  }

  public final void dropMemoryCaches() {
    if(myDoTrace) LOG.info("Drop memory caches " + myStorageFile);
    synchronized (myEnumerator) {
      doDropMemoryCaches();
    }
  }

  protected void doDropMemoryCaches() {
    myEnumerator.lockStorage();
    try {
      clearAppenderCaches();
    }
    finally {
      myEnumerator.unlockStorage();
    }
  }

  int getGarbageSize() {
    return (int)myLiveAndGarbageKeysCounter;
  }

  public File getBaseFile() {
    return myEnumerator.myFile;
  }

  @TestOnly // public for tests
  @SuppressWarnings("WeakerAccess") // used in upsource for some reason
  public boolean makesSenseToCompact() {
    if (myIsReadOnly) return false;

    final long fileSize = myValueStorage.getSize();
    final int megabyte = 1024 * 1024;

    if (fileSize > 5 * megabyte) { // file is longer than 5MB and (more than 50% of keys is garbage or approximate benefit larger than 100M)
      int liveKeys = (int)(myLiveAndGarbageKeysCounter / LIVE_KEY_MASK);
      int deadKeys = (int)(myLiveAndGarbageKeysCounter & DEAD_KEY_NUMBER_MASK);

      if (fileSize > 50 *  megabyte && forceNewCompact()) return true;
      if (deadKeys < 50) return false;

      final int benefitSize = 100 * megabyte;
      final long avgValueSize = fileSize / (liveKeys + deadKeys);

      return deadKeys > liveKeys ||
             avgValueSize *deadKeys > benefitSize ||
             myReadCompactionGarbageSize > fileSize / 2;
    }
    return false;
  }

  @NotNull
  private static File checkDataFiles(@NotNull final File file) {
    if (!file.exists()) {
      deleteFilesStartingWith(getDataFile(file));
    }
    return file;
  }

  public static void deleteFilesStartingWith(@NotNull File prefixFile) {
    IOUtil.deleteAllFilesStartingWith(prefixFile);
  }

  @NotNull
  private static File getDataFile(@NotNull final File file) {
    return new File(file.getParentFile(), file.getName() + DATA_FILE_EXTENSION);
  }

  private final TIntHashSet inlineKeysCache = new TIntHashSet();

  @Override
  public final void put(Key key, Value value) throws IOException {
    if (myIsReadOnly) throw new IncorrectOperationException();
    synchronized (myEnumerator) {
      if (myKeyDescriptor instanceof InlineKeyDescriptor) {
        inlineKeysCache.add(((InlineKeyDescriptor<Key>)myKeyDescriptor).toInt(key));
      }
      doPut(key, value);
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
      newValueOffset = myValueStorage.appendBytes(bytes.getInternalBuffer(), 0, bytes.size(), 0);
    }

    myEnumerator.lockStorage();
    try {
      myEnumerator.markDirty(true);
      myAppendCache.remove(key);

      long oldValueOffset;
      if (myDirectlyStoreLongFileOffsetMode) {
        if (myIntMapping) {
          ((PersistentBTreeEnumerator<Key>)myEnumerator).putNonnegativeValue(key, (Integer)value);
          return;
        }
        oldValueOffset = ((PersistentBTreeEnumerator<Key>)myEnumerator).getNonnegativeValue(key);
        ((PersistentBTreeEnumerator<Key>)myEnumerator).putNonnegativeValue(key, newValueOffset);
      } else {
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
      myEnumerator.unlockStorage();
    }
  }

  @Override
  public final int enumerate(Key name) throws IOException {
    if (myIsReadOnly) throw new IncorrectOperationException();
    synchronized (myEnumerator) {
      myIntAddressForNewRecord = canUseIntAddressForNewRecord(myValueStorage.getSize());
      return super.enumerate(name);
    }
  }

  public interface ValueDataAppender {
    void append(DataOutput out) throws IOException;
  }

  /**
   * Appends value chunk from specified appender to key's value.
   * Important use note: value externalizer used by this map should process all bytes from DataInput during deserialization and make sure
   * that deserialized value is consistent with value chunks appended.
   * E.g. Value can be Set of String and individual Strings can be appended with this method for particular key, when {@link #get()} will
   * be eventually called for the key, deserializer will read all bytes retrieving Strings and collecting them into Set
   * @throws IOException
   */
  public final void appendData(Key key, @NotNull ValueDataAppender appender) throws IOException {
    if (myIsReadOnly) throw new IncorrectOperationException();
    synchronized (myEnumerator) {
      doAppendData(key, appender);
    }
  }

  private static final ThreadLocalCachedValue<AppendStream> ourFlyweightAppenderStream = new ThreadLocalCachedValue<AppendStream>() {
    @Override
    protected AppendStream create() {
      return new AppendStream();
    }
  };

  protected void doAppendData(Key key, @NotNull ValueDataAppender appender) throws IOException {
    assert !myIntMapping;
    myEnumerator.markDirty(true);

    AppendStream appenderStream = ourFlyweightAppenderStream.getValue();
    BufferExposingByteArrayOutputStream stream = myAppendCache.get(key);
    appenderStream.setOut(stream);
    appender.append(appenderStream);
    appenderStream.setOut(null);
  }

  /**
   * Process all keys registered in the map. Note that keys which were removed after {@link #compact()} call will be processed as well. Use
   * {@link #processKeysWithExistingMapping(com.intellij.util.Processor)} to process only keys with existing mappings
   */
  @Override
  public final boolean processKeys(Processor<Key> processor) throws IOException {
    synchronized (myEnumerator) {
      myAppendCache.clear();
      long start = System.currentTimeMillis();
      boolean b = myEnumerator.iterateData(processor);
      System.out.println("process keys: file: " + myStorageFile.getPath() + " elapsed:" + (System.currentTimeMillis() - start));
      return b;
    }
  }

  @NotNull
  public Collection<Key> getAllKeysWithExistingMapping() throws IOException {
    final List<Key> values = new ArrayList<Key>();
    processKeysWithExistingMapping(new CommonProcessors.CollectProcessor<Key>(values));
    return values;
  }

  public final boolean processKeysWithExistingMapping(Processor<Key> processor) throws IOException {
    synchronized (myEnumerator) {
      myAppendCache.clear();
      return myEnumerator.processAllDataObject(processor, new PersistentEnumerator.DataFilter() {
        @Override
        public boolean accept(final int id) {
          return readValueId(id) != NULL_ADDR;
        }
      });
    }
  }

  public static final AtomicLong sumTime = new AtomicLong(0);
  public static final AtomicInteger num = new AtomicInteger(0);

  @Override
  public final Value get(Key key) throws IOException {
    synchronized (myEnumerator) {
      myBusyReading = true;
      long start = System.nanoTime();
      try {
        return doGet(key);
      } finally {
        long elapsed = System.nanoTime() - start;
//        System.out.println("file: " + myStorageFile.getPath() + " key: " + key + " elapsed:" + elapsed);
        num.incrementAndGet();
        sumTime.addAndGet(elapsed);
        myBusyReading = false;
      }
    }
  }

  public boolean isBusyReading() {
    return myBusyReading;
  }

  @Nullable
  protected Value doGet(Key key) throws IOException {
    final long valueOffset;
    final int id;

    myEnumerator.lockStorage();
    try {
      myAppendCache.remove(key);

      if (myDirectlyStoreLongFileOffsetMode) {
        valueOffset = ((PersistentBTreeEnumerator<Key>)myEnumerator).getNonnegativeValue(key);
        if (myIntMapping) {
          return (Value)(Integer)(int)valueOffset;
        }
        id = -1;
      } else {
        id = tryEnumerate(key);
        if (id == PersistentEnumerator.NULL_ID) {
          return null;
        }

        if (myIntMapping) {
          return (Value)(Integer)myEnumerator.myStorage.getInt(id + myParentValueRefOffset);
        }

        valueOffset = readValueId(id);
      }

      if (valueOffset == NULL_ADDR) {
        return null;
      }
    } finally {
      myEnumerator.unlockStorage();
    }

    final PersistentHashMapValueStorage.ReadResult readResult = myValueStorage.readBytes(valueOffset);

    DataInputStream input = new DataInputStream(new UnsyncByteArrayInputStream(readResult.buffer));
    final Value valueRead;
    try {
      valueRead = myValueExternalizer.read(input);
    }
    finally {
      input.close();
    }

    if (myValueStorage.performChunksCompaction(readResult.chunksCount, readResult.buffer.length)) {
      long newValueOffset = myValueStorage.compactChunks(new ValueDataAppender() {
        @Override
        public void append(DataOutput out) throws IOException {
          myValueExternalizer.save(out, valueRead);
        }
      }, readResult);

      myEnumerator.lockStorage();
      try {
        myEnumerator.markDirty(true);

        if (myDirectlyStoreLongFileOffsetMode) {
          ((PersistentBTreeEnumerator<Key>)myEnumerator).putNonnegativeValue(key, newValueOffset);
        } else {
          updateValueId(id, newValueOffset, valueOffset, key, 0);
        }
        myLiveAndGarbageKeysCounter++;
        myReadCompactionGarbageSize += readResult.buffer.length;
      } finally {
        myEnumerator.unlockStorage();
      }
    }
    return valueRead;
  }

  public final boolean containsMapping(Key key) throws IOException {
    synchronized (myEnumerator) {
      return doContainsMapping(key);
    }
  }

  protected boolean doContainsMapping(Key key) throws IOException {
    myEnumerator.lockStorage();
    try {
      myAppendCache.remove(key);
      if (myDirectlyStoreLongFileOffsetMode) {
        return ((PersistentBTreeEnumerator<Key>)myEnumerator).getNonnegativeValue(key) != NULL_ADDR;
      } else {
        final int id = tryEnumerate(key);
        if (id == PersistentEnumerator.NULL_ID) {
          return false;
        }
        if (myIntMapping) return true;
        return readValueId(id) != NULL_ADDR;
      }
    }
    finally {
      myEnumerator.unlockStorage();
    }
  }

  @Override
  public final void remove(Key key) throws IOException {
    if (myIsReadOnly) throw new IncorrectOperationException();
    synchronized (myEnumerator) {
      doRemove(key);
    }
  }

  protected void doRemove(Key key) throws IOException {
    myEnumerator.lockStorage();
    try {
      final long record;

      myAppendCache.remove(key);
      if (myDirectlyStoreLongFileOffsetMode) {
        assert !myIntMapping; // removal isn't supported
        record = ((PersistentBTreeEnumerator<Key>)myEnumerator).getNonnegativeValue(key);
        if (record != NULL_ADDR) {
          ((PersistentBTreeEnumerator<Key>)myEnumerator).putNonnegativeValue(key, NULL_ADDR);
        }
      } else {
        final int id = tryEnumerate(key);
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
      myEnumerator.unlockStorage();
    }
  }

  @Override
  public final void force() {
    if (myIsReadOnly) return;
    if(myDoTrace) LOG.info("Forcing " + myStorageFile);
    synchronized (myEnumerator) {
      doForce();
    }
  }

  protected void doForce() {
    myEnumerator.lockStorage();
    try {
      try {
        clearAppenderCaches();
      }
      finally {
        super.force();
      }
    }
    finally {
      myEnumerator.unlockStorage();
    }
  }

  private void clearAppenderCaches() {
    myAppendCache.clear();
    myValueStorage.force();
  }

  @Override
  public final void close() throws IOException {
    if(myDoTrace) LOG.info("Closed " + myStorageFile);
    synchronized (myEnumerator) {
      doClose();
    }
  }

  @Override
  public void clear() throws IOException {
    final File baseFile = getBaseFile();
    try {
      close();
    }
    catch (Throwable ignored) {
    }
    if (baseFile != null) {
      IOUtil.deleteAllFilesStartingWith(baseFile);
    }
  }

  private void doClose() throws IOException {
    myEnumerator.lockStorage();
    try {
      try {
        myAppendCacheFlusher.stop();
        myAppendCache.clear();
      }
      finally {
        final PersistentHashMapValueStorage valueStorage = myValueStorage;
        try {
          if (valueStorage != null) {
            valueStorage.dispose();
          }
        }
        finally {
          super.close();
        }
      }
    }
    finally {
      myEnumerator.unlockStorage();
    }
  }

  static class CompactionRecordInfo {
    final int key;
    final int  address;
    long valueAddress;
    long newValueAddress;
    byte[] value;

    public CompactionRecordInfo(int _key, long _valueAddress, int _address) {
      key = _key;
      address = _address;
      valueAddress = _valueAddress;
    }
  }

  // made public for tests
  public void compact() throws IOException {
    if (myIsReadOnly) throw new IncorrectOperationException();
    synchronized (myEnumerator) {
      force();
      LOG.info("Compacting "+myEnumerator.myFile.getPath());
      LOG.info("Live keys:" + ((int)(myLiveAndGarbageKeysCounter  / LIVE_KEY_MASK)) +
               ", dead keys:" + ((int)(myLiveAndGarbageKeysCounter & DEAD_KEY_NUMBER_MASK)) +
               ", read compaction size:" + myReadCompactionGarbageSize);

      final long now = System.currentTimeMillis();

      final File oldDataFile = getDataFile(myEnumerator.myFile);
      final String oldDataFileBaseName = oldDataFile.getName();
      final File[] oldFiles = getFilesInDirectoryWithNameStartingWith(oldDataFile, oldDataFileBaseName);

      final String newPath = getDataFile(myEnumerator.myFile).getPath() + ".new";
      final PersistentHashMapValueStorage newStorage = PersistentHashMapValueStorage.create(newPath, myIsReadOnly);
      myValueStorage.switchToCompactionMode();
      myEnumerator.markDirty(true);
      long sizeBefore = myValueStorage.getSize();

      myLiveAndGarbageKeysCounter = 0;
      myReadCompactionGarbageSize = 0;

      try {
        if (doNewCompact()) {
          newCompact(newStorage);
        } else {
          traverseAllRecords(new PersistentEnumerator.RecordsProcessor() {
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

      if (oldFiles != null) {
        for(File f:oldFiles) {
          assert FileUtil.deleteWithRenaming(f);
        }
      }

      final long newSize = newStorage.getSize();

      File newDataFile = new File(newPath);
      final String newBaseName = newDataFile.getName();
      final File[] newFiles = getFilesInDirectoryWithNameStartingWith(newDataFile, newBaseName);

      if (newFiles != null) {
        File parentFile = newDataFile.getParentFile();

        // newFiles should get the same names as oldDataFiles
        for (File f : newFiles) {
          String nameAfterRename = StringUtil.replace(f.getName(), newBaseName, oldDataFileBaseName);
          FileUtil.rename(f, new File(parentFile, nameAfterRename));
        }
      }

      myValueStorage = PersistentHashMapValueStorage.create(oldDataFile.getPath(), myIsReadOnly);
      LOG.info("Compacted " + myEnumerator.myFile.getPath() + ":" + sizeBefore + " bytes into " + newSize + " bytes in " + (System.currentTimeMillis() - now) + "ms.");
      myEnumerator.putMetaData(myLiveAndGarbageKeysCounter);
      myEnumerator.putMetaData2( myLargeIndexWatermarkId );
      if (myDoTrace) LOG.assertTrue(myEnumerator.isDirty());
    }
  }

  private static File[] getFilesInDirectoryWithNameStartingWith(File fileFromDirectory, final String baseFileName) {
    File parentFile = fileFromDirectory.getParentFile();
    return parentFile != null ?parentFile.listFiles(new FileFilter() {
      @Override
      public boolean accept(final File pathname) {
        return pathname.getName().startsWith(baseFileName);
      }
    }) : null;
  }

  private void newCompact(PersistentHashMapValueStorage newStorage) throws IOException {
    long started = System.currentTimeMillis();
    final List<CompactionRecordInfo> infos = new ArrayList<CompactionRecordInfo>(10000);

    traverseAllRecords(new PersistentEnumerator.RecordsProcessor() {
      @Override
      public boolean process(final int keyId) throws IOException {
        final long record = readValueId(keyId);
        if (record != NULL_ADDR) {
          infos.add(new CompactionRecordInfo(getCurrentKey(), record, keyId));
        }
        return true;
      }
    });

    LOG.info("Loaded mappings:"+(System.currentTimeMillis() - started) + "ms, keys:"+infos.size());
    started = System.currentTimeMillis();
    long fragments = 0;
    if (!infos.isEmpty()) {
      try {
        fragments = myValueStorage.compactValues(infos, newStorage);
      } catch (Throwable t) {
        if (!(t instanceof IOException)) throw new IOException("Compaction failed", t);
        throw (IOException)t;
      }
    }

    LOG.info("Compacted values for:"+(System.currentTimeMillis() - started) + "ms fragments:"+((int)fragments) + ", newfragments:"+(fragments >> 32));

    started = System.currentTimeMillis();
    try {
      myEnumerator.lockStorage();

      for(int i = 0; i < infos.size(); ++i) {
        CompactionRecordInfo info = infos.get(i);
        updateValueId(info.address, info.newValueAddress, info.valueAddress, null, info.key);
        myLiveAndGarbageKeysCounter += LIVE_KEY_MASK;
      }
    } finally {
      myEnumerator.unlockStorage();
    }
    LOG.info("Updated mappings:" + (System.currentTimeMillis() - started) + " ms");
  }

  private long readValueId(final int keyId) {
    if (myDirectlyStoreLongFileOffsetMode) {
      return ((PersistentBTreeEnumerator<Key>)myEnumerator).keyIdToNonnegattiveOffset(keyId);
    }
    long address = myEnumerator.myStorage.getInt(keyId + myParentValueRefOffset);
    if (address == 0 || address == -POSITIVE_VALUE_SHIFT) {
      return NULL_ADDR;
    }

    if (address < 0) {
      address = -address - POSITIVE_VALUE_SHIFT;
    } else {
      long value = (myEnumerator.myStorage.getInt(keyId + myParentValueRefOffset + 4)) & 0xFFFFFFFFL;
      address = ((address << 32) + value) & ~USED_LONG_VALUE_MASK;
    }

    return address;
  }

  private int smallKeys;
  private int largeKeys;
  private int transformedKeys;
  private int requests;

  private int updateValueId(int keyId, long value, long oldValue, @Nullable Key key, int processingKey) throws IOException {
    if (myDirectlyStoreLongFileOffsetMode) {
      ((PersistentBTreeEnumerator<Key>)myEnumerator).putNonnegativeValue(((InlineKeyDescriptor<Key>)myKeyDescriptor).fromInt(processingKey),
                                                                         value);
      return keyId;
    }
    final boolean newKey = oldValue == NULL_ADDR;
    if (newKey) ++requests;
    boolean defaultSizeInfo = true;

    if (myCanReEnumerate) {
      if (canUseIntAddressForNewRecord(value)) {
        defaultSizeInfo = false;
        myEnumerator.myStorage.putInt(keyId + myParentValueRefOffset, -(int)(value + POSITIVE_VALUE_SHIFT));
        if (newKey) ++smallKeys;
      } else {
        if ((keyId < myLargeIndexWatermarkId || myLargeIndexWatermarkId == 0) && (newKey || canUseIntAddressForNewRecord(oldValue))) {
          // keyId is result of enumerate, if we do reenumerate then it is no longer accessible unless somebody cached it
          myIntAddressForNewRecord = false;
          keyId = myEnumerator.reenumerate(key == null ? myEnumerator.getValue(keyId, processingKey) : key);
          ++transformedKeys;
          if (myLargeIndexWatermarkId == 0) {
            myLargeIndexWatermarkId = keyId;
          }
        }
      }
    }

    if (defaultSizeInfo) {
      value |= USED_LONG_VALUE_MASK;

      myEnumerator.myStorage.putInt(keyId + myParentValueRefOffset, (int)(value >>> 32) );
      myEnumerator.myStorage.putInt(keyId + myParentValueRefOffset + 4, (int)value);

      if (newKey) ++largeKeys;
    }

    if (newKey && IOStatistics.DEBUG && (requests & IOStatistics.KEYS_FACTOR_MASK) == 0) {
      IOStatistics.dump("small:"+smallKeys + ", large:" + largeKeys + ", transformed:"+transformedKeys +
                        ",@"+getBaseFile().getPath());
    }
    if (doHardConsistencyChecks) {
      long checkRecord = readValueId(keyId);
      if (checkRecord != (value & ~USED_LONG_VALUE_MASK)) {
        assert false:value;
      }
    }
    return keyId;
  }

  public String toString() {
    return super.toString() + ":"+myStorageFile;
  }
}
