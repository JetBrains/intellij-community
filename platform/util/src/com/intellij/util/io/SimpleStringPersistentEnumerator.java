// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Simple string enumerator, for small sets of values (10s-100s):
 * <p><ul>
 * <li>Strings are stored directly in UTF-8 encoding </li>
 * <li>Always has synchronized state between disk and memory state</li>
 * <li>Could have >1 id for same value
 * (i.e. it violates general {@link DataEnumerator} contract -- this is to keep backward-compatible behavior)</li>
 * <li>Uses CopyOnWrite for updating state, so {@link #valueOf(int)}/@{@link #enumerate(String)} are wait-free
 * for already existing value/id</li>
 * </ul>
 */
@ApiStatus.Internal
public final class SimpleStringPersistentEnumerator implements ScannableDataEnumeratorEx<String>,
                                                               CleanableStorage{
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
   * For backward-compatibility reasons, it could be >1 id for the same value -- i.e. it could be
   * idToValue.length > valueToId.size()
   */
  private volatile String @NotNull [] idToValue;

  public SimpleStringPersistentEnumerator(@NotNull Path file) {
    this(file, UTF_8);
  }

  public SimpleStringPersistentEnumerator(@NotNull Path file,
                                          @NotNull Charset charset) {
    this.file = file;
    this.charset = charset;

    Pair<Object2IntMap<String>, String[]> pair = readStorageFromDisk(file, charset, /*fallbackTo: */ Charset.defaultCharset());
    synchronized (this) {
      valueToId = pair.getFirst();
      idToValue = pair.getSecond();
    }
  }

  public @NotNull Path getFile() {
    return file;
  }

  @Override
  public int tryEnumerate(@Nullable String value) throws IOException {
    return valueToId.getInt(value);
  }

  @Override
  public int enumerate(@Nullable String value) {
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

    //CopyOnWrite: it is possible to do CoW without lock, with CAS only -- but it is hard to keep consistent
    //             on-disk representation: it is very possible to have correct version in memory, but outdated
    //             version stored on disk -- and additional efforts needed to prevent it.
    //             So locking seems to be the simpler choice: this enumerator is aimed for ~small datasets,
    //             i.e. total number of updates must be very limited.

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
    return new ArrayList<>(valueToId.keySet());
  }

  public @NotNull Map<String, Integer> getInvertedState() {
    return new HashMap<>(valueToId);
  }

  @Override
  public @Nullable String valueOf(int id) {
    String[] idToNameLocal = this.idToValue;
    if (id <= NULL_ID || id > idToNameLocal.length) {
      return null;
    }
    return idToNameLocal[id - 1];
  }

  @Override
  public boolean forEach(@NotNull ValueReader<? super String> reader) throws IOException {
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
    writeStorageToDisk(idToValue, file, charset);
  }

  public boolean isEmpty() {
    return getSize() == 0;
  }

  public int getSize() {
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
  public synchronized void closeAndClean() throws IOException {
    //FIXME RC: there is no way to 'close' this enumerator: the very next update just re-creates the file
    valueToId = new Object2IntOpenHashMap<>();
    idToValue = ArrayUtil.EMPTY_STRING_ARRAY;
    FileUtil.delete(file);
  }

  private static @NotNull Pair<Object2IntMap<String>, String[]> readStorageFromDisk(@NotNull Path file,
                                                                                    @NotNull Charset charset,
                                                                                    @NotNull Charset charsetToFallback) {
    if (Files.notExists(file)) {
      writeStorageToDisk(ArrayUtil.EMPTY_STRING_ARRAY, file, charset);
      return Pair.create(new Object2IntOpenHashMap<>(), ArrayUtil.EMPTY_STRING_ARRAY);
    }

    //RC: Why we need charsetToFallback: backward-compatibility reasons. For a long time, SimpleStringPersistentEnumerator
    //    actually used defaultCharset() to read-write data, even though it _promised_ to use UTF-8 -- so now we have
    //    to deal with files in a defaultCharset().
    //MAYBE RC: I think, after 1-2 releases it will be OK to remove 'charsetToFallback' branch, and use only UTF-8
    try {
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
      String[] idToNameRegistry = lines.isEmpty() ? ArrayUtil.EMPTY_STRING_ARRAY : new String[lines.size()];
      for (int i = 0; i < lines.size(); i++) {
        String name = lines.get(i);
        int id = i + 1;
        nameToIdRegistry.put(name, id);
        idToNameRegistry[i] = name;
      }
      return Pair.create(nameToIdRegistry, idToNameRegistry);
    }
    catch (IOException e) {
      LOG.warnWithDebug("Can't read [" + file.toAbsolutePath() + "] content", e);
      writeStorageToDisk(ArrayUtil.EMPTY_STRING_ARRAY, file, charset);
      return Pair.create(new Object2IntOpenHashMap<>(), ArrayUtil.EMPTY_STRING_ARRAY);
    }
  }

  private static void writeStorageToDisk(String[] idToName,
                                         @NotNull Path file,
                                         @NotNull Charset charset) {
    try {
      Files.createDirectories(file.getParent());
      Files.write(file, Arrays.asList(idToName), charset);
    }
    catch (IOException e) {
      throw new UncheckedIOException("Can't store enumerator to " + file, e);
    }
  }
}
