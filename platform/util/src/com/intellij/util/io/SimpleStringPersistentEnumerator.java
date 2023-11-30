// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtilRt;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.*;

/**
 * Simple string enumerator, for small sets of values (10s-100s):
 * <p><ul>
 * <li>Strings are stored directly in UTF-8 encoding </li>
 * <li>Always has synchronized state between disk and memory state -- which is why doesn't have methods
 * like 'flush' and 'isDirty', and doesn't implement {@link DurableDataEnumerator}</li>
 * <li>Could have >1 id for same value
 * (i.e. it violates general {@link DataEnumerator} contract -- this is to keep backward-compatible behavior)</li>
 * <li>Uses CopyOnWrite for updating state, so {@link #valueOf(int)}/@{@link #enumerate(String)} are wait-free
 * for already existing value/id</li>
 * </ul>
 * <p>
 * Enumerator does not NEED to be {@link #close()}-ed -- since it doesn't keep the file opened -- but it supports .close()
 * method, and fails to do anything if close()-ed.
 */
@ApiStatus.Internal
public final class SimpleStringPersistentEnumerator implements ScannableDataEnumeratorEx<String>,
                                                               Closeable,
                                                               CleanableStorage {
  private static final Logger LOG = Logger.getInstance(SimpleStringPersistentEnumerator.class);

  private final @NotNull Path file;
  private final Charset charset;

  /**
   * [value -> id] mapping
   * Updated with CopyOnWrite under 'this' lock.
   */
  private volatile @NotNull Object2IntMap<String> valueToId;
  /**
   * [id -> value] mapping.
   * Updated with CopyOnWrite under 'this' lock.
   * Since id starts with 1 (0 is reserved as NULL_ID), so index in the array is (id-1)
   * For backward-compatibility reasons, it could be >1 id for the same value -- i.e., it could be
   * `idToValue.length > valueToId.size()`
   */
  private volatile String @NotNull [] idToValue;

  private volatile boolean closed = false;

  public SimpleStringPersistentEnumerator(@NotNull Path file) {
    this(file, UTF_8);
  }

  public SimpleStringPersistentEnumerator(@NotNull Path file,
                                          @NotNull Charset charset) {
    this.file = file;
    this.charset = charset;

    try {
      if (Files.notExists(file)) {
        Files.createDirectories(file.getParent());
        Files.createFile(file);
      }

      Pair<Object2IntMap<String>, String[]> pair;
      try {
        pair = readStorageFromDisk(file, charset, /* fallbackTo: */ Charset.defaultCharset());
      }
      catch (IOException e) {
        LOG.warnWithDebug("Can't read [" + file.toAbsolutePath() + "] content", e);
        //clean the file:
        Files.write(file, ArrayUtil.EMPTY_BYTE_ARRAY, WRITE, TRUNCATE_EXISTING, CREATE);

        pair = readStorageFromDisk(file, charset, /* fallbackTo: */ Charset.defaultCharset());
      }


      synchronized (this) {
        valueToId = pair.getFirst();
        idToValue = pair.getSecond();
      }
    }
    catch (IOException e) {
      throw new UncheckedIOException("Can't create file [" + file + "]", e);
    }
  }

  public @NotNull Path getFile() {
    return file;
  }

  @Override
  public int tryEnumerate(@Nullable String value) throws IOException {
    checkNotClosed();

    return valueToId.getInt(value);
  }

  @Override
  public int enumerate(@Nullable String value) {
    checkNotClosed();

    Object2IntMap<String> valueToIdLocal = valueToId;
    int id = valueToIdLocal.getInt(value);
    if (id != valueToIdLocal.defaultReturnValue()) {
      return id;
    }

    return insertNewValue(value);
  }

  private synchronized int insertNewValue(@Nullable String value) {
    if (value != null && StringUtil.containsLineBreak(value)) {
      throw new IllegalArgumentException("SimpleStringPersistentEnumerator doesn't support multi-line strings: [" + value + "]");
    }

    //re-check under lock:
    int id = valueToId.getInt(value);
    if (id != valueToId.defaultReturnValue()) {
      return id;
    }

    //CopyOnWrite: it is possible to do CoW without a lock, with CAS only -- but it is hard to keep consistent
    //             on-disk representation: it is very possible to have a correct version in memory, but outdated
    //             version stored on disk -- and additional efforts needed to prevent it.
    //             So locking seems to be the simpler choice: this enumerator is aimed for ~small datasets,
    //             i.e., total number of updates must be very limited.

    // do not use nameToId.size because enumeration file may have duplicates on different lines
    int newId = idToValue.length + 1;

    String[] newIdToName = Arrays.copyOf(idToValue, idToValue.length + 1);
    newIdToName[newId - 1] = value;
    idToValue = newIdToName;

    Object2IntMap<String> newValueToId = new Object2IntOpenHashMap<>(valueToId);
    newValueToId.put(value, newId);
    valueToId = newValueToId;

    forceDiskSync();

    return newId;
  }

  public @NotNull Collection<String> entries() {
    checkNotClosed();
    return new ArrayList<>(valueToId.keySet());
  }

  public @NotNull Map<String, Integer> getInvertedState() {
    checkNotClosed();
    return new HashMap<>(valueToId);
  }

  @Override
  public @Nullable String valueOf(int id) {
    checkNotClosed();

    String[] idToNameLocal = this.idToValue;
    if (id <= NULL_ID || id > idToNameLocal.length) {
      return null;
    }
    return idToNameLocal[id - 1];
  }

  @Override
  public boolean forEach(@NotNull ValueReader<? super String> reader) throws IOException {
    checkNotClosed();
    String[] idToNameLocal = idToValue;
    for (int i = 0; i < idToNameLocal.length; i++) {
      String value = idToNameLocal[i];
      int id = i + 1;
      boolean continueProcessing = reader.read(id, value);
      if (!continueProcessing) {
        return false;
      }
    }
    return true;
  }

  @Override
  public int recordsCount() throws IOException {
    return getSize();
  }

  public synchronized void forceDiskSync() {
    checkNotClosed();
    writeStorageToDisk(idToValue, file, charset);
  }

  public boolean isEmpty() {
    return getSize() == 0;
  }

  public int getSize() {
    checkNotClosed();
    //TODO RC: better use idToName.length -- which is really shows enumerator size (=number of entries)
    //         Current implementation is checked by tests, but it seems there is no prod-code usages that
    //         rely on current impl
    return valueToId.size();
  }

  public @NotNull String dumpToString() {
    String[] idToValueLocal = idToValue;
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < idToValueLocal.length; i++) {
      String value = idToValueLocal[i];
      sb.append(value).append(" -> ").append(i + 1).append('\n');
    }
    return sb.toString();
  }

  @Override
  public synchronized void close() throws IOException {
    this.closed = true;
  }

  @Override
  public synchronized void closeAndClean() throws IOException {
    close();
    valueToId = new Object2IntOpenHashMap<>();
    idToValue = ArrayUtilRt.EMPTY_STRING_ARRAY;
    NioFiles.deleteRecursively(file);
  }

  private void checkNotClosed() {
    if (closed) {
      throw new IllegalStateException("Storage already closed");
      //TODO RC: ClosedStorageException would be better, but .enumerate() doesn't declare IOException
    }
  }

  private static @NotNull Pair<Object2IntMap<String>, String[]> readStorageFromDisk(@NotNull Path file,
                                                                                    @NotNull Charset charset,
                                                                                    @NotNull Charset charsetToFallback) throws IOException {
    //RC: Why we need charsetToFallback: backward-compatibility reasons. For a long time, SimpleStringPersistentEnumerator
    //    actually used defaultCharset() to read-write data, even though it _promised_ to use UTF-8 -- so now we have
    //    to deal with files in a defaultCharset().
    //MAYBE RC: I think, after 1-2 releases it will be OK to remove 'charsetToFallback' branch, and use only UTF-8
    List<String> lines;
    try {
      lines = Files.readAllLines(file, charset);
    }
    catch (IOException exMainCharset) {
      //maybe it is CharacterCodingException? Try reading with fallback charset
      try {
        lines = Files.readAllLines(file, charsetToFallback);
      }
      catch (IOException exFallbackCharset) {
        exFallbackCharset.addSuppressed(exMainCharset);
        throw exFallbackCharset;
      }
    }

    Object2IntMap<String> nameToIdRegistry = new Object2IntOpenHashMap<>(lines.size());
      String[] idToNameRegistry = lines.isEmpty() ? ArrayUtilRt.EMPTY_STRING_ARRAY : new String[lines.size()];
    for (int i = 0; i < lines.size(); i++) {
      String name = lines.get(i);
      int id = i + 1;
      nameToIdRegistry.put(name, id);
      idToNameRegistry[i] = name;
    }
    return Pair.create(nameToIdRegistry, idToNameRegistry);
  }

  private static void writeStorageToDisk(String[] idToName,
                                         @NotNull Path file,
                                         @NotNull Charset charset) {
    try {
      //Don't create folder/file here -- create (if not exist) the file only once, in ctor, and
      // after that -- fail if folder/file doesn't exist, because that means folder/file was removed,
      // which is very suspicious case and shouldn't be silently 'fixed':
      Files.write(file, Arrays.asList(idToName), charset, WRITE);
    }
    catch (IOException e) {
      if (Files.notExists(file)) {
        throw new UncheckedIOException("Can't store enumerator to " + file + " -- file is removed?", e);
      }
      throw new UncheckedIOException("Can't store enumerator to " + file, e);
    }
  }
}
