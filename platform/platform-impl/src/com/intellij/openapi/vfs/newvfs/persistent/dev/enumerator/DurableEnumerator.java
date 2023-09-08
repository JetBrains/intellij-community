// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev.enumerator;

import com.intellij.openapi.vfs.newvfs.persistent.dev.intmultimaps.IntToMultiIntMap;
import com.intellij.openapi.vfs.newvfs.persistent.mapped.MMappedFileStorage;
import com.intellij.openapi.vfs.newvfs.persistent.dev.appendonlylog.AppendOnlyLog;
import com.intellij.openapi.vfs.newvfs.persistent.dev.appendonlylog.AppendOnlyLogOverMMappedFile;
import com.intellij.util.Processor;
import com.intellij.util.io.ScannableDataEnumeratorEx;
import com.intellij.util.io.VersionUpdatedException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Supplier;


/**
 * Persistent enumerator for objects.
 * 'Durable' is to separate it from {@link com.intellij.util.io.PersistentEnumerator}, which is conceptually
 * the same, but right now tightly bounded to BTree-based implementation.
 * <p>
 * Implementation uses append-only log to store objects, and some (pluggable) Map[object.hash -> id*].
 */
public final class DurableEnumerator<K> implements ScannableDataEnumeratorEx<K>, Flushable, Closeable {

  public static final int DATA_FORMAT_VERSION = 1;

  public static final int PAGE_SIZE = 8 << 20;

  private final AppendOnlyLog keysLog;

  private final @NotNull KeyDescriptorEx<K> keyDescriptor;

  //MAYBE RC: we actually don't need _durable_ map here. We could go with
  //          1) in-memory map, transient & re-populated from log on each start
  //          2) swappable in-memory/on-disk map, there on-disk part is transient and
  //             map is re-populated from log on each start
  //          3) on-disk map, durable between restarts re-populated from log only on
  //             corruption
  private final @NotNull IntToMultiIntMap keyHashToId;

  public static <K> DurableEnumerator<K> open(@NotNull Path storagePath,
                                              @NotNull KeyDescriptorEx<K> keyDescriptor,
                                              @NotNull Supplier<IntToMultiIntMap> mapFactory) throws IOException {
    AppendOnlyLog appendOnlyLog = openLog(storagePath);
    return new DurableEnumerator<>(
      keyDescriptor,
      appendOnlyLog,
      mapFactory
    );
  }

  public DurableEnumerator(@NotNull KeyDescriptorEx<K> keyDescriptor,
                           @NotNull AppendOnlyLog appendOnlyLog,
                           @NotNull Supplier<IntToMultiIntMap> mapFactory) throws IOException {
    this.keyDescriptor = keyDescriptor;
    this.keysLog = appendOnlyLog;
    this.keyHashToId = mapFactory.get();

    //MAYBE RC: Could be filled async -- to not delay initialization
    //MAYBE RC: Extract this loading from ctor? I.e. define that map should already be populated, same as keysLog is.
    //          This way sync/async loading would be a property of factory(-ies), not ctor.
    this.keysLog.forEachRecord((logId, buffer) -> {
      K key = this.keyDescriptor.read(buffer);
      int keyHash = this.keyDescriptor.hashCodeOf(key);
      int id = logIdToEnumeratorId(logId);
      keyHashToId.put(keyHash, id);
      return true;
    });
  }

  private static @NotNull AppendOnlyLog openLog(@NotNull Path storagePath) throws IOException {
    AppendOnlyLogOverMMappedFile keysLog = new AppendOnlyLogOverMMappedFile(
      new MMappedFileStorage(storagePath, PAGE_SIZE)
    );
    int dataFormatVersion = keysLog.getDataVersion();
    if (dataFormatVersion == 0) {//FIXME RC: check log is empty for this branch
      keysLog.setDataVersion(DATA_FORMAT_VERSION);
    }
    else if (dataFormatVersion != DATA_FORMAT_VERSION) {
      keysLog.close();
      throw new VersionUpdatedException(storagePath, DATA_FORMAT_VERSION, dataFormatVersion);
    }
    return keysLog;
  }

  @Override
  public void flush() throws IOException {
    keysLog.flush(true);
    keyHashToId.flush();
  }

  @Override
  public void close() throws IOException {
    keysLog.close();
    keyHashToId.close();
  }

  @Override
  public int enumerate(@Nullable K key) throws IOException {
    if (key == null) {
      return NULL_ID;
    }
    return lookupOrCreateIdForKey(key);
  }

  @Override
  public int tryEnumerate(@Nullable K key) throws IOException {
    if (key == null) {
      return NULL_ID;
    }

    return lookupIdForKey(key);
  }

  @Override
  public @Nullable K valueOf(int keyId) throws IOException {
    if (keyId == NULL_ID) {
      return null;
    }
    return keysLog.read(keyId, keyDescriptor::read);
  }

  @Override
  public boolean processAllDataObjects(@NotNull Processor<? super K> processor) throws IOException {
    return keysLog.forEachRecord((recordId, buffer) -> {
      K key = keyDescriptor.read(buffer);
      return processor.process(key);
    });
  }

  /**
   * append-log identifies records by _long_ id, while enumerator API uses _int_ ids -- the method does the
   * conversion
   */
  private static int logIdToEnumeratorId(long logRecordId) {
    return Math.toIntExact(logRecordId);
  }

  private int lookupIdForKey(@NotNull K key) throws IOException {
    int keyHash = keyDescriptor.hashCodeOf(key);
    return keyHashToId.lookup(keyHash, candidateId -> {
      K candidateKey = keysLog.read(candidateId, keyDescriptor::read);
      return keyDescriptor.areEqual(candidateKey, key);
    });
  }

  private int lookupOrCreateIdForKey(@NotNull K key) throws IOException {
    int keyHash = keyDescriptor.hashCodeOf(key);
    return keyHashToId.lookupOrInsert(
      keyHash,
      candidateId -> {
        K candidateKey = keysLog.read(candidateId, keyDescriptor::read);
        return keyDescriptor.areEqual(candidateKey, key);
      },
      _keyHash_ -> {
        long logRecordId = keyDescriptor.saveToLog(key, keysLog);
        return logIdToEnumeratorId(logRecordId);
      });
  }
}
