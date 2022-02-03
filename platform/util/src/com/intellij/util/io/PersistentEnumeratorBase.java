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

import com.intellij.openapi.Forceable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.CommonProcessors;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.SystemProperties;
import com.intellij.util.indexing.impl.IndexDebugProperties;
import com.intellij.util.io.keyStorage.AppendableObjectStorage;
import com.intellij.util.io.keyStorage.AppendableStorageBackedByResizableMappedFile;
import com.intellij.util.io.keyStorage.InlinedKeyStorage;
import com.intellij.util.io.keyStorage.NoDataException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author max
 * @author jeka
 */
public abstract class PersistentEnumeratorBase<Data> implements DataEnumeratorEx<Data>, Forceable, Closeable, SelfDiagnosing {
  protected static final Logger LOG = Logger.getInstance(PersistentEnumeratorBase.class);
  protected static final int NULL_ID = DataEnumeratorEx.NULL_ID;

  protected static final boolean USE_RW_LOCK = SystemProperties.getBooleanProperty("idea.persistent.data.use.read.write.lock", false);
  private static final int META_DATA_OFFSET = 4;
  static final int DATA_START = META_DATA_OFFSET + 16;

  protected final ResizeableMappedFile myStorage;
  @NotNull
  protected final AppendableObjectStorage<Data> myKeyStorage;
  final KeyDescriptor<Data> myDataDescriptor;
  protected final Path myFile;
  private final Version myVersion;
  private final boolean myDoCaching;
  private final ReentrantReadWriteLock myLock = new ReentrantReadWriteLock(true);

  private volatile boolean myDirtyStatusUpdateInProgress;

  private boolean myClosed;
  private boolean myDirty;
  private boolean myCorrupted;
  private RecordBufferHandler<PersistentEnumeratorBase<?>> myRecordHandler;
  private @Nullable Flushable myMarkCleanCallback;

  public static class Version {
    private static final int DIRTY_MAGIC = 0xbabe1977;
    private static final int CORRECTLY_CLOSED_MAGIC = 0xebabafd;

    private final int correctlyClosedMagic;
    private final int dirtyMagic;

    public Version(int version) {
      this(CORRECTLY_CLOSED_MAGIC + version, DIRTY_MAGIC);
    }

    private Version(int _correctlyClosedMagic, int _dirtyMagic) {
      correctlyClosedMagic = _correctlyClosedMagic;
      dirtyMagic = _dirtyMagic;
      assert correctlyClosedMagic != dirtyMagic;
    }
  }

  abstract static class RecordBufferHandler<T extends PersistentEnumeratorBase<?>> {
    abstract int recordWriteOffset(T enumerator, byte[] buf) throws IOException;
    abstract byte @NotNull [] getRecordBuffer(T enumerator);
    abstract void setupRecord(T enumerator, int hashCode, final int dataOffset, final byte[] buf);
  }

  /**
   * @deprecated use {@link com.intellij.util.io.CorruptedException} instead.
   */
  @Deprecated
  public static class CorruptedException extends com.intellij.util.io.CorruptedException {
    public CorruptedException(Path file) {
      super("PersistentEnumerator storage corrupted " + file);
    }

    protected CorruptedException(String message) {
      super(message);
    }
  }

  public PersistentEnumeratorBase(@NotNull Path file,
                                  @NotNull ResizeableMappedFile storage,
                                  @NotNull KeyDescriptor<Data> dataDescriptor,
                                  int initialSize,
                                  @NotNull Version version,
                                  @NotNull RecordBufferHandler<? extends PersistentEnumeratorBase<?>> recordBufferHandler,
                                  boolean doCaching) throws IOException {
    myDataDescriptor = dataDescriptor;
    myFile = file;
    myVersion = version;
    myRecordHandler = (RecordBufferHandler<PersistentEnumeratorBase<?>>)recordBufferHandler;
    myDoCaching = doCaching;

    if (!Files.exists(file)) {
      if (file.getFileSystem().isReadOnly()) {
        throw new IOException(file + " in " + file.getFileSystem() + " is not exist");
      }
      FileUtil.delete(keyStreamFile());
      if (!FileUtil.createIfDoesntExist(file.toFile())) {
        throw new IOException("Cannot create empty file: " + file);
      }
    }

    myStorage = storage;

    boolean created = false;
    lockStorageWrite();
    try {
      if (myStorage.length() == 0) {
        try {
          markDirty(true);
          putMetaData(0);
          putMetaData2(0);
          setupEmptyFile();
          doFlush();
          created = true;
        }
        catch (RuntimeException e) {
          LOG.info(e);
          myStorage.close();
          if (e.getCause() instanceof IOException) {
            throw (IOException)e.getCause();
          }
          throw e;
        }
        catch (IOException e) {
          LOG.info(e);
          myStorage.close();
          throw e;
        }
        catch (Exception e) {
          LOG.info(e);
          myStorage.close();
          throw new CorruptedException(file);
        }
      }
      else {
        int sign;
        try {
          sign = myStorage.getInt(0);
        }
        catch(Exception e) {
          LOG.info(e);
          sign = myVersion.dirtyMagic;
        }
        if (sign != myVersion.correctlyClosedMagic) {
          myStorage.close();
          if (sign != myVersion.dirtyMagic) throw new VersionUpdatedException(file);
          throw new CorruptedException(file);
        }
      }
    }
    finally {
      unlockStorageWrite();
    }

    if (dataDescriptor instanceof InlineKeyDescriptor) {
      myKeyStorage = new InlinedKeyStorage<>((InlineKeyDescriptor<Data>)dataDescriptor);
    }
    else {
      try {
        myKeyStorage = new AppendableStorageBackedByResizableMappedFile<>(keyStreamFile(),
                                                                          initialSize,
                                                                          myStorage.getStorageLockContext(),
                                                                          PagedFileStorage.MB,
                                                                          false,
                                                                          dataDescriptor);
      }
      catch (Throwable e) {
        LOG.info(e);
        myStorage.close();
        throw new CorruptedException(file);
      }
    }

    if (IndexDebugProperties.IS_UNIT_TEST_MODE) {
      LOG.info("PersistentEnumeratorBase at " + myFile + " has been open (new = " + created + ")");
    }
  }

  protected void assertWriteAccess() {
    assert myLock.isWriteLockedByCurrentThread();
  }

  @NotNull
  protected Lock getWriteLock() {
    return myLock.writeLock();
  }

  @NotNull
  protected Lock getReadLock() {
    return USE_RW_LOCK ? myLock.readLock() : myLock.writeLock();
  }

  void lockStorageRead() {
    myStorage.lockRead();
  }

  void unlockStorageRead() {
    myStorage.unlockRead();
  }

  void lockStorageWrite() {
    myStorage.lockWrite();
  }

  void unlockStorageWrite() {
    myStorage.unlockWrite();
  }

  protected abstract void setupEmptyFile() throws IOException;

  @NotNull
  final RecordBufferHandler<PersistentEnumeratorBase<?>> getRecordHandler() {
    return myRecordHandler;
  }

  public void setRecordHandler(@NotNull RecordBufferHandler<PersistentEnumeratorBase<?>> recordHandler) {
    myRecordHandler = recordHandler;
  }

  void setMarkCleanCallback(@NotNull Flushable markCleanCallback) {
    myMarkCleanCallback = markCleanCallback;
  }

  public Data getValue(int keyId, int processingKey) throws IOException {
    return valueOf(keyId);
  }

  @Override
  public int tryEnumerate(Data value) throws IOException {
    return doEnumerate(value, true, false);
  }

  private int doEnumerate(Data value, boolean onlyCheckForExisting, boolean saveNewValue) throws IOException {
    if (myDoCaching && !saveNewValue) {
      int cachedId = PersistentEnumeratorCache.getCachedId(value, this);
      if (cachedId != NULL_ID) return cachedId;
    }

    int id = catchCorruption(() -> {
      return enumerateImpl(value, onlyCheckForExisting, saveNewValue);
    });

    if (myDoCaching && id != NULL_ID) {
      PersistentEnumeratorCache.cacheId(value, id, this);
    }

    return id;
  }

  @Override
  public int enumerate(Data value) throws IOException {
    return doEnumerate(value, false, false);
  }

  public interface DataFilter {
    boolean accept(int id) throws IOException;
  }

  protected void putMetaData(long data) throws IOException {
    lockStorageWrite();
    try {
      if (myStorage.length() < META_DATA_OFFSET + 8 || getMetaData() != data) myStorage.putLong(META_DATA_OFFSET, data);
    }
    finally {
      unlockStorageWrite();
    }
  }

  protected long getMetaData() throws IOException {
    lockStorageRead();
    try {
      return myStorage.getLong(META_DATA_OFFSET);
    }
    finally {
      unlockStorageRead();
    }
  }

  void putMetaData2(long data) throws IOException {
    lockStorageWrite();
    try {
      if (myStorage.length() < META_DATA_OFFSET + 16 || getMetaData2() != data) myStorage.putLong(META_DATA_OFFSET + 8, data);
    }
    finally {
      unlockStorageWrite();
    }
  }

  long getMetaData2() throws IOException {
    lockStorageRead();
    try {
      return myStorage.getLong(META_DATA_OFFSET + 8);
    }
    finally {
      unlockStorageRead();
    }
  }

  public boolean processAllDataObject(@NotNull final Processor<? super Data> processor, @Nullable final DataFilter filter) throws IOException {
    return traverseAllRecords(new RecordsProcessor() {
      @Override
      public boolean process(final int record) throws IOException {
        if (filter == null || filter.accept(record)) {
          return processor.process(valueOf(record));
        }
        return true;
      }
    });

  }

  @NotNull
  public Collection<Data> getAllDataObjects(@Nullable final DataFilter filter) throws IOException {
    final List<Data> values = new ArrayList<>();
    processAllDataObject(new CommonProcessors.CollectProcessor<>(values), filter);
    return values;
  }

  public abstract static class RecordsProcessor {
    private int myKey;

    public abstract boolean process(int record) throws IOException;
    void setCurrentKey(int key) {
      myKey = key;
    }
    int getCurrentKey() {
      return myKey;
    }
  }

  public abstract boolean traverseAllRecords(RecordsProcessor p) throws IOException;

  protected abstract int enumerateImpl(final Data value, final boolean onlyCheckForExisting, boolean saveNewValue) throws IOException;

  protected boolean isKeyAtIndex(final Data value, final int idx) throws IOException {
    if (myKeyStorage instanceof InlinedKeyStorage) return false;

    // check if previous serialized state is the same as for value
    // this is much faster than myDataDescriptor.isEqualTo(valueOf(idx), value) for identical objects
    // TODO: key storage lock
    final int addr = indexToAddr(idx);

    if (myKeyStorage.checkBytesAreTheSame(addr, value)) return true;

    if (myDataDescriptor instanceof DifferentSerializableBytesImplyNonEqualityPolicy) return false;
    return myDataDescriptor.isEqual(valueOf(idx), value);
  }

  protected int writeData(final Data value, int hashCode) {
    try {
      markDirty(true);

      final int dataOff = doWriteData(value);

      return setupValueId(hashCode, dataOff);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public int getLargestId() {
    return myKeyStorage.getCurrentLength();
  }

  protected int doWriteData(Data value) throws IOException {
    return myKeyStorage.append(value);
  }

  protected int setupValueId(int hashCode, int dataOff) throws IOException {
    final byte[] buf = myRecordHandler.getRecordBuffer(this);
    myRecordHandler.setupRecord(this, hashCode, dataOff, buf);
    final int pos = myRecordHandler.recordWriteOffset(this, buf);
    myStorage.put(pos, buf, 0, buf.length);

    return pos;
  }

  public boolean iterateData(@NotNull Processor<? super Data> processor) throws IOException {
    return doIterateData((offset, data) -> processor.process(data));
  }

  protected boolean doIterateData(@NotNull AppendableObjectStorage.StorageObjectProcessor<? super Data> processor) throws IOException {
    lockStorageWrite(); // todo locking in key storage
    try {
      myKeyStorage.force();
    }
    finally {
      unlockStorageWrite();
    }

    return myKeyStorage.processAll(processor);
  }

  private Path keyStreamFile() {
    return myFile.resolveSibling(myFile.getFileName() + ".keystream");
  }

  @Override
  public Data valueOf(int idx) throws IOException {
    if (idx <= NULL_ID) return null;
    return catchCorruption(() -> {
      return findValueFor(idx);
    });
  }

  private Data findValueFor(int idx) throws IOException {
    lockStorageRead();
    try {
      int addr = indexToAddr(idx);
      return myKeyStorage.read(addr);
    }
    finally {
      unlockStorageRead();
    }
  }

  int reEnumerate(Data key) throws IOException {
    if (!canReEnumerate()) throw new IncorrectOperationException();
    return doEnumerate(key, false, true);
  }

  boolean canReEnumerate() {
    return false;
  }

  protected abstract int indexToAddr(int idx) throws IOException;

  @Override
  public void close() throws IOException {
    getWriteLock().lock();
    try {
      lockStorageWrite();
      try {
        if (!myClosed) {
          myClosed = true;
          doClose();
          if (IndexDebugProperties.IS_UNIT_TEST_MODE) {
            LOG.info("PersistentEnumeratorBase at " + myFile + " has been closed");
          }
        }
      }
      finally {
        unlockStorageWrite();
      }
    }
    finally {
      getWriteLock().unlock();
    }
  }

  protected void doClose() throws IOException {
    IOCancellationCallbackHolder.interactWithUI();
    try {
      force();
      myKeyStorage.close();
    }
    finally {
      myStorage.close();
    }
  }

  public boolean isClosed() {
    getReadLock().lock();
    try {
      return myClosed;
    }
    finally {
      getReadLock().unlock();
    }
  }

  @Override
  public boolean isDirty() {
    lockStorageRead();
    try {
      return myDirty;
    }
    finally {
      unlockStorageRead();
    }
  }

  public boolean isCorrupted() {
    lockStorageRead();
    try {
      return myCorrupted;
    }
    finally {
      unlockStorageRead();
    }
  }

  protected void doFlush() throws IOException {
    markDirty(false);
    myStorage.force();
  }

  @Override
  public void force() {
    getWriteLock().lock();
    try {
      lockStorageWrite();
      try {
        myKeyStorage.force();
        if (myStorage.isDirty() || isDirty()) {
          doFlush();
        }
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
      finally {
        unlockStorageWrite();
      }
    }
    finally {
      getWriteLock().unlock();
    }
  }

  protected final void markDirty(boolean dirty) throws IOException {
    if (dirty && myDirty && !myDirtyStatusUpdateInProgress) return;
    lockStorageWrite();
    try {
      if (myDirty) {
        if (!dirty) {
          myDirtyStatusUpdateInProgress = true;
          if (myMarkCleanCallback != null) myMarkCleanCallback.flush();
          if (!myCorrupted) {
            myStorage.putInt(0, myVersion.correctlyClosedMagic);
            myDirty = false;
          }
          myDirtyStatusUpdateInProgress = false;
        }
      }
      else {
        if (dirty) {
          myDirtyStatusUpdateInProgress = true;
          myStorage.putInt(0, myVersion.dirtyMagic);
          myDirtyStatusUpdateInProgress = false;
          myDirty = true;
        }
      }
    }
    finally {
      unlockStorageWrite();
    }
  }

  protected void markCorrupted() {
    if (IndexDebugProperties.IS_UNIT_TEST_MODE) {
      dumpKeysOnCorruption();
    }

    lockStorageWrite();
    try {
      if (!myCorrupted) {
        myCorrupted = true;
        if (LOG.isDebugEnabled()) LOG.debug("Marking corrupted:" + myFile, new Throwable());
        try {
          markDirty(true);
          force();
        }
        catch (IOException e) {
          // ignore...
        }
      }
    }
    finally {
      unlockStorageWrite();
    }
  }

  protected void dumpKeysOnCorruption() {
  }

  protected boolean trySelfHeal() {
    return false;
  }

  @VisibleForTesting
  protected <V> V catchCorruption(ThrowableComputable<V, IOException> operation) throws IOException {
    if (isCorrupted()) {
      throw new CorruptedException(myFile);
    }

    try {
      // try to repair a storage
      try {
        return operation.compute();
      }
      catch (Throwable th) {
        if (th instanceof NoDataException || !trySelfHeal()) {
          throw th;
        }
      }

      // and try one more time to execute an operation
      return operation.compute();
    }
    catch (NoDataException e) {
      return null;
    }
    catch (IOException io) {
      markCorrupted();
      throw io;
    }
    catch (Throwable e) {
      markCorrupted();
      throw new RuntimeException(e);
    }
  }
}
