// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev;

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
import java.nio.file.Path;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Persistent enumerator for strings.
 * Uses append-only log to store strings, and in-memory Map[string.hash->id*].
 */
public class DurableStringEnumerator implements ScannableDataEnumeratorEx<String>, Forceable, Closeable {

  public static final int DATA_FORMAT_VERSION = 1;

  public static final int PAGE_SIZE = 8 << 20;

  private final AppendOnlyLog valuesLog;

  //@GuardedBy("hashToId")
  private final Int2IntMultimap valueHashToId = new Int2IntMultimap();

  //FIXME RC: currently .enumerate() and .tryEnumerate() execute under global lock, i.e. not scalable.
  //          It is not worse than PersistentEnumerator does, but we could do better -- we just need
  //          concurrent Map[int->int*] which is definitely possible


  public DurableStringEnumerator(@NotNull Path storagePath) throws IOException {
    this.valuesLog = openValuesLog(storagePath);
    //fill the in-memory mapping:
    //MAYBE RC: could be filled async -- to not delay initialization
    this.valuesLog.forEachRecord((logId, buffer) -> {
      String value = IOUtil.readString(buffer);
      int id = Math.toIntExact(logId);
      int valueHash = hashOf(value);
      valueHashToId.put(valueHash, id);
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

        byte[] valueBytes = value.getBytes(UTF_8);
        long appendedId = valuesLog.append(valueBytes);
        int id = Math.toIntExact(appendedId);
        valueHashToId.put(valueHash, id);
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
    if (valueId == NULL_ID) {
      return null;
    }
    return valuesLog.read(valueId, IOUtil::readString);
  }

  private static int hashOf(@NotNull String value) {
    int hash = value.hashCode();
    if (hash == Int2IntMultimap.NO_VALUE) {
      //Int2IntMultimap doesn't allow 0 keys/values, hence replace 0 hash with just any value!=0. Hash doesn't
      // identify name uniquely anyway, hence this replacement just adds another hash collision -- basically,
      // we replaced original String hashcode with our own, which avoids 0 at the cost of slightly higher chances
      // of collisions
      return -DATA_FORMAT_VERSION;// any value!=0 will do
    }
    return hash;
  }

  @Override
  public boolean processAllDataObjects(@NotNull Processor<? super String> processor) throws IOException {
    return valuesLog.forEachRecord((recordId, buffer) -> {
      String value = IOUtil.readString(buffer);
      return processor.process(value);
    });
  }

  @Override
  public void close() throws IOException {
    valuesLog.close();
  }

  //@GuardedBy("valueHashToId")
  private int lookupValue(@NotNull String value,
                          int hash) {
    IntRef foundIdRef = new IntRef(NULL_ID);
    valueHashToId.get(hash, candidateId -> {
      try {
        String candidateValue = valuesLog.read(candidateId, IOUtil::readString);
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
}
