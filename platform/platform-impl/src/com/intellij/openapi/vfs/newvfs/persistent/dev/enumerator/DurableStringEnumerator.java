// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev.enumerator;

import com.intellij.openapi.Forceable;
import com.intellij.openapi.util.IntRef;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSRecordsLockFreeOverMMappedFile.MMappedFileStorage;
import com.intellij.openapi.vfs.newvfs.persistent.dev.InvertedFilenameHashBasedIndex.Int2IntMultimap;
import com.intellij.openapi.vfs.newvfs.persistent.dev.appendonlylog.AppendOnlyLog;
import com.intellij.openapi.vfs.newvfs.persistent.dev.appendonlylog.AppendOnlyLogOverMMappedFile;
import com.intellij.util.Processor;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.ScannableDataEnumeratorEx;
import com.intellij.util.io.VersionUpdatedException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jsoup.UncheckedIOException;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Persistent enumerator for strings.
 * Uses append-only log to store strings, and in-memory Map[string.hash->id*].
 */
public final class DurableStringEnumerator implements ScannableDataEnumeratorEx<String>, Forceable, Closeable {

  public static final int DATA_FORMAT_VERSION = 1;

  public static final int PAGE_SIZE = 8 << 20;

  private final AppendOnlyLog valuesLog;

  //@GuardedBy("hashToId")
  private final Int2IntMultimap valueHashToId = new Int2IntMultimap();

  //@GuardedBy("hashToId")
  private volatile int maxId = -1;

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


  public DurableStringEnumerator(@NotNull Path storagePath) throws IOException {
    this.valuesLog = openValuesLog(storagePath);
    //fill the in-memory mapping:
    //MAYBE RC: could be filled async -- to not delay initialization
    this.valuesLog.forEachRecord((logId, buffer) -> {
      String value = readString(buffer);
      int id = Math.toIntExact(logId);

      int valueHash = hashOf(value);

      valueHashToId.put(valueHash, id);
      maxId = Math.max(id, maxId);
      return true;
    });
  }

  private static @NotNull AppendOnlyLogOverMMappedFile openValuesLog(@NotNull Path storagePath) throws IOException {
    AppendOnlyLogOverMMappedFile valuesLog = new AppendOnlyLogOverMMappedFile(
      new MMappedFileStorage(storagePath, PAGE_SIZE)
    );
    int dataFormatVersion = valuesLog.getDataVersion();
    if (dataFormatVersion == 0) {//FIXME RC: also check log is empty
      valuesLog.setDataVersion(DATA_FORMAT_VERSION);
    }
    else if (dataFormatVersion != DATA_FORMAT_VERSION) {
      valuesLog.close();
      throw new VersionUpdatedException(storagePath, DATA_FORMAT_VERSION, dataFormatVersion);
    }
    return valuesLog;
  }

  @Override
  public boolean isDirty() {
    return false;
  }

  @Override
  public void force() throws IOException {
    valuesLog.flush(true);
  }

  @Override
  public int enumerate(@Nullable String value) throws IOException {
    if (value == null) {
      return NULL_ID;
    }
    try {
      int valueHash = hashOf(value);
      synchronized (valueHashToId) {
        int foundId = lookupValue(value, valueHash);
        if (foundId != NULL_ID) {
          return foundId;
        }

        int id = writeString(value, valuesLog);

        valueHashToId.put(valueHash, id);
        maxId = Math.max(maxId, id);
        return id;
      }
    }
    catch (UncheckedIOException uiox) {
      throw uiox.ioException();
    }
  }

  @Override
  public int tryEnumerate(@Nullable String value) throws IOException {
    if (value == null) {
      return NULL_ID;
    }
    int valueHash = hashOf(value);
    synchronized (valueHashToId) {
      return lookupValue(value, valueHash);
    }
  }

  @Override
  public @Nullable String valueOf(int valueId) throws IOException {
    //FIXME RC: DataEnumerator.valueOf() specifies that it must return null for unknown (i.e. not enumerated before)
    //          ids. Current implementation doesn't do that, generally: id is supplied to valuesLog, which most
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

    if (valueId <= NULL_ID || valueId > maxId) {
      return null;
    }
    return valuesLog.read(valueId, DurableStringEnumerator::readString);
  }

  @Override
  public boolean processAllDataObjects(@NotNull Processor<? super String> processor) throws IOException {
    return valuesLog.forEachRecord((recordId, buffer) -> {
      String value = readString(buffer);
      return processor.process(value);
    });
  }

  @Override
  public void close() throws IOException {
    valuesLog.close();
  }

  // ===================== implementation: =============================================================== //

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

  //@GuardedBy("valueHashToId")
  private int lookupValue(@NotNull String value,
                          int hash) {
    IntRef foundIdRef = new IntRef(NULL_ID);
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
    int id = Math.toIntExact(appendedId);
    return id;
  }
}
