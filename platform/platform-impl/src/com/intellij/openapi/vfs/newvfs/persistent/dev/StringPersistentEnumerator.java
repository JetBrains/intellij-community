// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev;

import com.intellij.openapi.Forceable;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSRecordsLockFreeOverMMappedFile.MMappedFileStorage;
import com.intellij.openapi.vfs.newvfs.persistent.dev.appendonlylog.AppendOnlyLogOverMMappedFile;
import com.intellij.util.Processor;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.ScannableDataEnumeratorEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jsoup.UncheckedIOException;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Persistent enumerator for strings. Uses append-only log to store strings, and in-memory Map[string->id].
 * 
 */
public class StringPersistentEnumerator implements ScannableDataEnumeratorEx<String>, Forceable, Closeable {

  public static final int PAGE_SIZE = 8 << 20;

  private final AppendOnlyLogOverMMappedFile valuesLog;

  private final ConcurrentHashMap<String, Integer> valueToId = new ConcurrentHashMap<>();

  public StringPersistentEnumerator(@NotNull Path storagePath) throws IOException {
    this.valuesLog = new AppendOnlyLogOverMMappedFile(
      new MMappedFileStorage(storagePath, PAGE_SIZE)
    );
    //fill the in-memory mapping:
    valuesLog.forEachRecord((id, buffer) -> {
      String string = IOUtil.readString(buffer);
      valueToId.put(string, Integer.valueOf((int)id));
      return true;
    });
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
      return valueToId.computeIfAbsent(value, _value_ -> {
        try {
          int valueId = (int)valuesLog.append(_value_.getBytes(UTF_8));
          return Integer.valueOf(valueId);
        }
        catch (IOException ex) {
          throw new UncheckedIOException(ex);
        }
      });
    }
    catch (UncheckedIOException uiox) {
      throw uiox.ioException();
    }
  }

  @Override
  public int tryEnumerate(@Nullable String value) throws IOException {
    return valueToId.getOrDefault(value, NULL_ID);
  }

  @Override
  public @Nullable String valueOf(int valueId) throws IOException {
    if (valueId == NULL_ID) {
      return null;
    }
    return valuesLog.read(valueId, IOUtil::readString);
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
    valueToId.clear();
  }
}
