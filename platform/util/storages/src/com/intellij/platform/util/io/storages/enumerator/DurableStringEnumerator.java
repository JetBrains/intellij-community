// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.enumerator;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.IntRef;
import com.intellij.platform.util.io.storages.StorageFactory;
import com.intellij.platform.util.io.storages.appendonlylog.AppendOnlyLog;
import com.intellij.platform.util.io.storages.appendonlylog.AppendOnlyLogFactory;
import com.intellij.platform.util.io.storages.intmultimaps.Int2IntMultimap;
import com.intellij.util.io.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Persistent enumerator for strings.
 * Uses append-only log to store strings, and in-memory Map[string.hash->id*].
 * Suitable for moderately big enumerators that are used very intensively, so increased heap consumption pays off.
 * <p/>
 * For general cases use {@link DurableEnumerator} -- benchmarks show it is just ~10-15% slower than
 * {@link DurableStringEnumerator} for most ops.
 */
@ApiStatus.Internal
public final class DurableStringEnumerator implements DurableDataEnumerator<String>,
                                                      ScannableDataEnumeratorEx<String>,
                                                      Unmappable, CleanableStorage {

  public static final int DATA_FORMAT_VERSION = 1;

  public static final int PAGE_SIZE = 8 << 20;


  private final AppendOnlyLog valuesLog;

  private final @NotNull CompletableFuture<Int2IntMultimap> valueHashToIdFuture;

  private final Object valueHashLock = new Object();

  /** Lazily initialized in {@link #valueHashToId()} */
  //@GuardedBy("valueHashLock")
  private Int2IntMultimap valueHashToId = null;

  //FIXME RC: currently .enumerate() and .tryEnumerate() execute under global lock, i.e. not scalable.
  //          It is not worse than PersistentEnumerator does, but we could do better -- we just need
  //          concurrent Map[int->int*] which is definitely possible

  //TODO RC: DateEnumerator contract specifies that .valueOf(id) returns null if id is unknown to the enumerator.
  //         This is not true for current implementation: we deliver the id to appendOnlyLog, which usually
  //         throws exception if id is unknown -- but sometimes could just read random garbage. We could protect
  //         against that (and satisfy the DEnumerator contract) by keeping set of really enumerated id.
  //         But this creates an overhead I'd like to avoid, because in correct usage it should be no 'unknown id'
  //         in use -- and it seems silly to pay the (quite high) price for something what shouldn't happen anyway.
  //         Better option could be to
  //         1. Specify .valueOf in this class violates original contract, and always throw exception on unknown
  //            ids (except NULL_ID).
  //         2. Keep the set of enumerated ids, but only under feature-flag, enabled in debug versions -- and disable
  //            it in prod, there (supposingly) all mistakes are already fixed

  public DurableStringEnumerator(@NotNull AppendOnlyLog valuesLog,
                                 @NotNull Int2IntMultimap valueHashToId) {
    this(valuesLog, CompletableFuture.completedFuture(valueHashToId));
  }

  public DurableStringEnumerator(@NotNull AppendOnlyLog valuesLog,
                                 @NotNull CompletableFuture<Int2IntMultimap> valueHashToIdFuture) {
    this.valuesLog = valuesLog;
    this.valueHashToIdFuture = valueHashToIdFuture;
  }

  private static final StorageFactory<? extends AppendOnlyLog> VALUES_LOG_FACTORY = AppendOnlyLogFactory
    .withDefaults()
    .pageSize(PAGE_SIZE)
    .failIfDataFormatVersionNotMatch(DATA_FORMAT_VERSION)
    .checkIfFileCompatibleEagerly(true)
    .cleanIfFileIncompatible();

  public static @NotNull DurableStringEnumerator open(@NotNull Path storagePath) throws IOException {
    return VALUES_LOG_FACTORY.wrapStorageSafely(
      storagePath,
      valuesLog -> new DurableStringEnumerator(
        valuesLog,
        buildValueToIdIndex(valuesLog)
      )
    );
  }

  public interface AsyncExecutor {
    <T> @NotNull CompletableFuture<T> async(@NotNull Callable<T> task);
  }

  public static @NotNull DurableStringEnumerator openAsync(
    @NotNull Path storagePath,
    @NotNull AsyncExecutor executor
  ) throws IOException {
    return VALUES_LOG_FACTORY.wrapStorageSafely(
      storagePath,
      valuesLog -> {
        return new DurableStringEnumerator(
          valuesLog,
          executor.async(() -> buildValueToIdIndex(valuesLog))
        );
      });
  }

  @Override
  public boolean isDirty() {
    //TODO RC: with mapped files we actually don't know are there any unsaved changes,
    //         since OS is responsible for that. We could force OS to flush the changes,
    //         but we couldn't ask are there changes.
    //         I think return false is +/- safe option, since the data is almost always
    //         'safe' (as long as OS doesn't crash), but it is a bit logically inconsistent:
    //         .isDirty() is supposed to return false if .force() has nothing to do, but
    //         .force() still _can_ something, i.e. forcing OS to flush.
    return false;
  }

  @Override
  public void force() throws IOException {
    valuesLog.flush();
  }

  @Override
  public int enumerate(@Nullable String value) throws IOException {
    if (value == null) {
      return NULL_ID;
    }
    int valueHash = hashOf(value);
    synchronized (valueHashLock) {
      Int2IntMultimap valueHashToId = valueHashToId();
      int foundId = lookupValue(valueHashToId, value, valueHash);
      if (foundId != NULL_ID) {
        return foundId;
      }

      int id = writeString(value, valuesLog);

      valueHashToId.put(valueHash, id);
      return id;
    }
  }

  @Override
  public int tryEnumerate(@Nullable String value) throws IOException {
    if (value == null) {
      return NULL_ID;
    }
    int valueHash = hashOf(value);
    synchronized (valueHashLock) {
      Int2IntMultimap valueHashToId = valueHashToId();
      return lookupValue(valueHashToId, value, valueHash);
    }
  }

  @Override
  public @Nullable String valueOf(int valueId) throws IOException {
    //FIXME RC: DataEnumerator.valueOf() specifies that it must return null for unknown (i.e. not enumerated before)
    //          ids. Current implementation generally doesn't comply: id is supplied to valuesLog, which most
    //          likely throws some random exception if supplied id is not a valid record id.
    //          We could 'fix' it by keeping a set of valid ids here, in enumerator -- but this is quite a memory
    //          consumption (almost same as .valueHashToId -- which is already quite noticeable), and also adds to
    //          .valueOf() execution time -- which we want to be as fast as possible. And all this for (almost)
    //          nothing: generally speaking, supplying random/unknown ids to enumerator is (almost always) a bug in
    //          code -- it shouldn't happen in a regular scenarios.
    //          Two approaches how to manage it:
    //          1. Implement the .knownIds set, but only under feature-flag, and enable it in DEBUG builds, but
    //             not in prod
    //          2. Adjust AppendOnlyLog so it stores recordId in the record itself, and checks it on read.
    //             This is less taxing on memory (consumes native/mapped instead of heap), and also concurrent,
    //             and also makes more sense by itself.

    if (!valuesLog.isValidId(valueId)) {
      return null;
    }
    return valuesLog.read(valueId, DurableStringEnumerator::readString);
  }

  @Override
  public boolean forEach(@NotNull ValueReader<? super String> reader) throws IOException {
    return valuesLog.forEachRecord((recordId, buffer) -> {
      int valueId = convertLogIdToValueId(recordId);
      String value = readString(buffer);
      return reader.read(valueId, value);
    });
  }

  @Override
  public int recordsCount() throws IOException {
    synchronized (valueHashLock) {
      return valueHashToId().size();
    }
  }

  @Override
  public void close() throws IOException {
    try {
      //We must ensure scanning is finished _before_ we close valuesLog -- because we expect (e.g. in .closeAndUnsafelyUnmap()
      // and/or .closeAndClean()) that closed enumerator does not use the file/mapped buffers anymore.

      //BEWARE: Don't call valueHashToIdFuture.cancel() here!
      //        Future.cancel() doesn't _require_ to actually cancel the running task (even with `interruptIfRunning) -- but
      //        .cancel() makes .join()/.get() return immediately, (because 'result'=cancellation is already known).
      //        By default .join() waits until task is finished -- successfully or exceptionally, doesn't matter, either
      //        way if .join() terminates => task is not running anymore. But .cancel() breaks than invariant: since result
      //        of the Future is already known (cancellation), .join()/.get() don't need to wait for task to actually finish
      //        In this scenario it leads to SIGSEGV (Access Violation) if un-mmap follows close() -- while valueHash building
      //        async task is still running.
      valueHashToIdFuture.join();
    }
    catch (CancellationException e) {
      //just ignore
    }
    catch (Throwable e) {
      Logger.getInstance(DurableStringEnumerator.class).info(".valueHashToId computation failed", e);
    }

    valuesLog.close();
  }

  @Override
  public void closeAndUnsafelyUnmap() throws IOException {
    close();
    if (valuesLog instanceof Unmappable) {
      ((Unmappable)valuesLog).closeAndUnsafelyUnmap();
    }
  }

  @Override
  public void closeAndClean() throws IOException {
    close();
    valuesLog.closeAndClean();
  }

  // ===================== implementation: =============================================================== //

  //@GuardedBy("valueHashLock")
  private @NotNull Int2IntMultimap valueHashToId() throws IOException {
    try {
      if (valueHashToId == null) {
        valueHashToId = valueHashToIdFuture.get();
      }
      return valueHashToId;
    }
    catch (InterruptedException e) {
      throw new IOException(e);
    }
    catch (ExecutionException e) {
      throw new IOException(e.getCause());
    }
  }

  private static int hashOf(@NotNull String value) {
    int hash = value.hashCode();
    if (hash == Int2IntMultimap.NO_VALUE) {
      //Int2IntMultimap doesn't allow 0 keys/values, hence replace 0 hash with just any value!=0. Hash doesn't
      // identify name uniquely anyway, hence this replacement just adds another hash collision -- basically,
      // we replaced original String hashcode with our own, which avoids 0 at the cost of slightly higher chances
      // of collisions
      return -1;// any value!=0 will do
    }
    return hash;
  }

  //@GuardedBy("valueHashLock")
  private int lookupValue(@NotNull Int2IntMultimap valueHashToId,
                          @NotNull String value,
                          int hash) throws IOException {
    IntRef foundIdRef = new IntRef(NULL_ID);
    try {
      valueHashToId.lookup(hash, candidateId -> {
        try {
          String candidateValue = valuesLog.read(candidateId, DurableStringEnumerator::readString);
          if (candidateValue.equals(value)) {
            foundIdRef.set(candidateId);
            return false;//stop
          }
          return true;
        }
        catch (IOException ex) {
          throw new UncheckedIOException(ex);
        }
      });
      return foundIdRef.get();
    }
    catch (UncheckedIOException ex) {
      throw ex.getCause();
    }
  }

  //MAYBE RC: instead of converting string bytes to/from UTF8 -- maybe just store String fields as-is?
  //          i.e. access private .value and .coder fields, and write/read their values? -- this allows
  //          to bypass 1 array copy, and probably also a character encoding/decoding

  private static @NotNull String readString(@NotNull ByteBuffer buffer) {
    return IOUtil.readString(buffer);
  }

  private static int writeString(@NotNull String value,
                                 @NotNull AppendOnlyLog valuesLog) throws IOException {
    byte[] valueBytes = value.getBytes(UTF_8);
    long appendedId = valuesLog.append(valueBytes);
    return convertLogIdToValueId(appendedId);
  }


  private static @NotNull Int2IntMultimap buildValueToIdIndex(@NotNull AppendOnlyLog valuesLog) throws IOException {
    Int2IntMultimap valueHashToId = new Int2IntMultimap();
    valuesLog.forEachRecord((logId, buffer) -> {
      String value = readString(buffer);
      int id = convertLogIdToValueId(logId);

      int valueHash = hashOf(value);

      valueHashToId.put(valueHash, id);
      return true;
    });
    return valueHashToId;
  }

  private static int convertLogIdToValueId(long logId) {
    return Math.toIntExact(logId);
  }
}
