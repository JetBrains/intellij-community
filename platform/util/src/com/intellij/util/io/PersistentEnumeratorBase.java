// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.openapi.Forceable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.util.*;
import com.intellij.util.indexing.impl.IndexDebugProperties;
import com.intellij.util.io.keyStorage.AppendableObjectStorage;
import com.intellij.util.io.keyStorage.AppendableStorageBackedByResizableMappedFile;
import com.intellij.util.io.keyStorage.InlinedKeyStorage;
import com.intellij.util.io.keyStorage.NoDataException;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;
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
@Internal
public abstract class PersistentEnumeratorBase<Data> implements DataEnumeratorEx<Data>,
                                                                ScannableDataEnumeratorEx<Data>,
                                                                Forceable, Closeable, SelfDiagnosing {
  protected static final Logger LOG = Logger.getInstance(PersistentEnumeratorBase.class);

  protected static final boolean USE_RW_LOCK = SystemProperties.getBooleanProperty("idea.persistent.data.use.read.write.lock", false);
  private static final int META_DATA_OFFSET = 4;
  static final int DATA_START = META_DATA_OFFSET + 16;

  protected final ResizeableMappedFile myCollisionResolutionStorage;
  protected final @NotNull AppendableObjectStorage<Data> myKeyStorage;
  final KeyDescriptor<Data> myDataDescriptor;
  protected final Path myFile;
  private final Version myVersion;
  private final boolean myDoCaching;

  /**
   * Lock protects enumerator internal state.
   * If acquired, the lock must always be acquired _before_ storage lock ({@link #lockStorageWrite()}/{@link #lockStorageWrite()})
   * <p>
   * TODO RC: initially RW lock was considered, but was found quite hard to find really read-only
   * ops, so now it is used only as exclusive lock (i.e. only writeLock part is acquired for both
   * read and write ops)
   * <p>
   * FIXME RC: it seems that this lock is not really needed: all its acquisition are immediately
   * followed by acquisition of apt. storage lock. Tried to remove it, but got stuck on a read
   * lock part: i.e. right now all getReadLock().lock() statements really acquire exclusive write
   * lock, not shared read lock -- hence by replacing getReadLock().lock() with lockStorageRead()
   * we change semantics. If we replace getReadLock() with lockStorageWrite() -- we keep semantics,
   * but increase contention on storage write lock -- which is already quite contended.
   */
  private final ReentrantReadWriteLock myLock = new ReentrantReadWriteLock();

  private volatile boolean myDirtyStatusUpdateInProgress;

  private boolean myClosed;
  private volatile boolean myDirty;
  private volatile boolean myCorrupted;
  private RecordBufferHandler<PersistentEnumeratorBase<?>> myRecordHandler;
  private @Nullable Flushable myMarkCleanCallback;

  public static final class Version {
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

  protected abstract static class RecordBufferHandler<T extends PersistentEnumeratorBase<?>> {
    abstract int recordWriteOffset(T enumerator, byte[] buf) throws IOException;

    abstract byte @NotNull [] getRecordBuffer(T enumerator);

    abstract void setupRecord(T enumerator, int hashCode, final int dataOffset, final byte[] buf);
  }

  protected PersistentEnumeratorBase(@NotNull Path file,
                                     @NotNull ResizeableMappedFile valueStorage,
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
    myCollisionResolutionStorage = valueStorage;

    lockStorageWrite();
    try {
      if (!Files.exists(file)) {
        if (file.getFileSystem().isReadOnly()) {
          throw new IOException(file + " in " + file.getFileSystem() + " is not exist");
        }

        Path parent = file.getParent();
        if (parent != null) {
          Files.createDirectories(parent);
        }
        Files.createFile(file);
      }

      boolean created = false;
      if (myCollisionResolutionStorage.length() == 0) {
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
          if (e.getCause() instanceof IOException) {
            throw (IOException)e.getCause();
          }
          throw e;
        }
        catch (IOException e) {
          LOG.info(e);
          throw e;
        }
        catch (Exception e) {
          LOG.info(e);
          throw new CorruptedException("PersistentEnumerator storage corrupted " + file, e);
        }
      }
      else {
        int sign;
        try {
          sign = myCollisionResolutionStorage.getInt(0);
        }
        catch (Exception e) {
          LOG.info(e);
          sign = myVersion.dirtyMagic;
        }
        if (sign != myVersion.correctlyClosedMagic) {
          if (sign != myVersion.dirtyMagic) {
            throw new VersionUpdatedException(file, Integer.toHexString(myVersion.correctlyClosedMagic), Integer.toHexString(sign));
          }
          else {
            throw new CorruptedException("PersistentEnumerator storage corrupted " + file);
          }
        }
      }

      if (dataDescriptor instanceof InlineKeyDescriptor) {
        myKeyStorage = new InlinedKeyStorage<>((InlineKeyDescriptor<Data>)dataDescriptor);
      }
      else {
        try {
          myKeyStorage = new AppendableStorageBackedByResizableMappedFile<>(
            keyStreamFile(),
            initialSize,
            myCollisionResolutionStorage.getStorageLockContext(),
            /*pageSize: */ IOUtil.MiB,
            false,
            dataDescriptor
          );
        }
        catch (Throwable e) {
          LOG.info(e);
          throw new CorruptedException(file);
        }
      }

      if (IndexDebugProperties.IS_UNIT_TEST_MODE && LOG.isTraceEnabled()) {
        LOG.debug("PersistentEnumeratorBase at " + myFile + " has been open (new = " + created + ")");
      }
    }
    catch (Throwable t) {
      //Close the valueStorage on any error in a single place:
      final Exception errorOnClose = ExceptionUtil.runAndCatch(
        valueStorage::close
      );
      if (errorOnClose != null) {
        t.addSuppressed(errorOnClose);
      }
      throw t;
    }
    finally {
      unlockStorageWrite();
    }
  }

  protected @NotNull Lock getWriteLock() {
    return myLock.writeLock();
  }

  protected @NotNull Lock getReadLock() {
    return USE_RW_LOCK ? myLock.readLock() : myLock.writeLock();
  }

  void lockStorageRead() {
    myCollisionResolutionStorage.lockRead();
  }

  void unlockStorageRead() {
    myCollisionResolutionStorage.unlockRead();
  }

  void lockStorageWrite() {
    myCollisionResolutionStorage.lockWrite();
  }

  void unlockStorageWrite() {
    myCollisionResolutionStorage.unlockWrite();
  }

  protected abstract void setupEmptyFile() throws IOException;

  final @NotNull RecordBufferHandler<PersistentEnumeratorBase<?>> getRecordHandler() {
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
      if (myCollisionResolutionStorage.length() < META_DATA_OFFSET + 8 || getMetaData() != data) {
        myCollisionResolutionStorage.putLong(META_DATA_OFFSET, data);
      }
    }
    finally {
      unlockStorageWrite();
    }
  }

  protected long getMetaData() throws IOException {
    lockStorageRead();
    try {
      return myCollisionResolutionStorage.getLong(META_DATA_OFFSET);
    }
    finally {
      unlockStorageRead();
    }
  }

  void putMetaData2(long data) throws IOException {
    lockStorageWrite();
    try {
      if (myCollisionResolutionStorage.length() < META_DATA_OFFSET + 16 || getMetaData2() != data) {
        myCollisionResolutionStorage.putLong(META_DATA_OFFSET + 8, data);
      }
    }
    finally {
      unlockStorageWrite();
    }
  }

  long getMetaData2() throws IOException {
    lockStorageRead();
    try {
      return myCollisionResolutionStorage.getLong(META_DATA_OFFSET + 8);
    }
    finally {
      unlockStorageRead();
    }
  }

  public boolean processAllDataObject(final @NotNull Processor<? super Data> processor,
                                      final @Nullable DataFilter filter)
    throws IOException {
    return traverseAllRecords(new RecordsProcessor() {
      @Override
      public boolean process(int record) throws IOException {
        if (filter == null || filter.accept(record)) {
          return processor.process(valueOf(record));
        }
        return true;
      }
    });
  }

  public boolean forEach(@NotNull ValueReader<? super Data> reader,
                         @Nullable DataFilter filter) throws IOException {
    return traverseAllRecords(new RecordsProcessor() {
      @Override
      public boolean process(final int record) throws IOException {
        if (filter == null || filter.accept(record)) {
          Data value = valueOf(record);
          return reader.read(record, value);
        }
        return true;
      }
    });
  }

  @Override
  public boolean forEach(@NotNull ValueReader<? super Data> reader) throws IOException {
    return forEach(reader, /*filter: */null);
  }

  public @NotNull Collection<Data> getAllDataObjects(final @Nullable DataFilter filter) throws IOException {
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
    Data actualValue = valueOf(idx);
    if (actualValue == null) {
      return value == null;
    }
    return myDataDescriptor.isEqual(actualValue, value);
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
    myCollisionResolutionStorage.put(pos, buf, 0, buf.length);

    return pos;
  }

  public boolean iterateData(@NotNull Processor<? super Data> processor) throws IOException {
    return iterateData((offset, data) -> processor.process(data));
  }

  boolean iterateData(@NotNull AppendableObjectStorage.StorageObjectProcessor<? super Data> processor) throws IOException {
    return myKeyStorage.processAll(processor);
  }

  private Path keyStreamFile() {
    return myFile.resolveSibling(myFile.getFileName() + ".keystream");
  }

  @Override
  public Data valueOf(@Range(from = 1, to = Integer.MAX_VALUE) int idx) throws IOException {
    //noinspection ConstantValue
    if (idx <= NULL_ID) return null;
    return catchCorruption(() -> {
      return findValueFor(idx);
    });
  }

  private Data findValueFor(@Range(from = 1, to = Integer.MAX_VALUE) int idx) throws IOException {
    boolean shouldLock = shouldLockOnValueOf();
    if (shouldLock) {
      lockStorageRead();
    }
    try {
      int addr = indexToAddr(idx);
      return myKeyStorage.read(addr, shouldLock);
    }
    finally {
      if (shouldLock) {
        unlockStorageRead();
      }
    }
  }

  protected abstract boolean shouldLockOnValueOf();

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
          if (IndexDebugProperties.IS_UNIT_TEST_MODE && LOG.isTraceEnabled()) {
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
    IOCancellationCallbackHolder.INSTANCE.interactWithUI();

    getWriteLock().lock();
    try {
      try {
        force();
        myKeyStorage.close();
      }
      finally {
        myCollisionResolutionStorage.close();
      }
    }
    finally {
      getWriteLock().unlock();
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
    return myDirty;
  }

  public boolean isCorrupted() {
    return myCorrupted;
  }

  protected void doFlush() throws IOException {
    markDirty(false);
    myCollisionResolutionStorage.force();
  }

  @Override
  public void force() {
    if (!isDirty()) return;
    getWriteLock().lock();
    try {
      lockStorageWrite();
      try {
        if (isDirty()) {
          if (myKeyStorage.isDirty()) {
            myKeyStorage.force();
          }
          if (myCollisionResolutionStorage.isDirty()) {
            doFlush();
          }
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
            myCollisionResolutionStorage.putInt(0, myVersion.correctlyClosedMagic);
            myDirty = false;
          }
          myDirtyStatusUpdateInProgress = false;
        }
      }
      else {
        if (dirty) {
          myDirtyStatusUpdateInProgress = true;
          myCollisionResolutionStorage.putInt(0, myVersion.dirtyMagic);
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
    if (IndexDebugProperties.IS_UNIT_TEST_MODE && LOG.isTraceEnabled()) {
      dumpKeysOnCorruption();
    }

    getWriteLock().lock();
    try {
      if (!myCorrupted) {
        myCorrupted = true;
        if (LOG.isDebugEnabled()) LOG.debug("Marking corrupted:" + myFile, new Throwable());
        try {
          StorageLockContext lockContext = myCollisionResolutionStorage.getStorageLockContext();


          //WARNING: POTENTIALLY TRIGGERING CONTENT
          //    Both .markDirty() and .force() acquire storage.writeLock() -- because they (potentially) modify the
          //    storage. But this is deadlock-prone, because markCorrupted() could be called from otherwise-read-only
          //    methods (e.g. PersistentMapImpl.doGet()), there lockStorageRead() is acquired.
          //    (attempt to acquire readLock under writeLock is a deadlock for regular j.u.c.ReentrantReadWriteLock)
          //
          //    I found no simple-and-correct way to untangle it: Locks management in PersistentHMap/Enumerator/ResizeableMappedFile
          //    is quite complicated already, because there are no clear abstraction borders, and PHM regularly puts its
          //    hands into the Enumerator implementation internals. Don't want to complicate it even more for a single
          //    .markCorrupted() method.
          //
          //    It seems like the least-effort + least-intrusive way to avoid deadlock is to temporarily release readLock(s),
          //    if acquired, and re-acquire them back after markDirty()+force. This creates a window where no locks are acquired
          //    -- an opportunity to corrupt the enumerator/map state -- but we're in the .markCorrupted() method already, what
          //    could be any more corrupted? Jokes aside: we expect .markCorrupted() to be called infrequently, and to deadlock
          //    the whole app is definitely worse than to corrupt a bit more storage that is already corrupted.
          //
          //    Basically, I suggest seeing markCorrupted() method as cursed, and spoiled with dark arts -- scapegoat for all
          //    the PHM sins.

          runWithStorageReadLocksTemporaryReleased(lockContext, () -> {
            markDirty(true);
            force();
          });
        }
        catch (IOException e) {
          // ignore...
        }
      }
    }
    finally {
      getWriteLock().unlock();
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
      throw new CorruptedException("PersistentEnumerator storage corrupted " + myFile);
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
    catch (ClosedStorageException e) {
      throw e;
    }
    catch (IOException io) {
      LOG.error(io);
      markCorrupted();
      throw io;
    }
    catch (Throwable e) {
      LOG.error(e);
      markCorrupted();
      throw new RuntimeException(e);
    }
  }

  /**
   * Release all lockContext's readLocks, held by current thread,
   * run the task given,
   * and re-acquire back all the readLocks released
   */
  private static void runWithStorageReadLocksTemporaryReleased(@NotNull StorageLockContext lockContext,
                                                               @NotNull ThrowableRunnable<IOException> task) throws IOException {
    int readLocksActuallyReleased = 0;
    Lock readLock = lockContext.readLock();
    try {
      int readLocksToRelease = lockContext.readLockHolds();
      //readLocksActuallyReleased counts locks _actually released_ -- i.e. if the loop terminates earlier than readLocksToRelease,
      // we'll acquire back only the number of locks we've actually released, not more.
      while (readLocksActuallyReleased < readLocksToRelease) {
        readLock.unlock();
        readLocksActuallyReleased++;
      }

      task.run();
    }
    finally {
      for (int i = 0; i < readLocksActuallyReleased; i++) {
        readLock.lock();
      }
    }
  }
}
