// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Simple version of string enumerator:
 * <p><ul>
 * <li>strings are stored directly in UTF-8 encoding FIXME RC: this is not true, actual implementation uses defaultCharset()!
 * <li>always has synchronized state between disk and memory state
 * </ul>
 */
@ApiStatus.Internal
public final class SimpleStringPersistentEnumerator implements DataEnumerator<String> {
  private final @NotNull Path myFile;
  private final Charset charset;

  private final @NotNull Object2IntMap<String> myInvertedState;
  private final @NotNull Int2ObjectMap<String> myForwardState;

  public SimpleStringPersistentEnumerator(final @NotNull Path file) {
    //FIXME RC: javadoc states that charset should be UTF-8, but implementation actually used defaultCharset()
    //          Need to make doc & impl consistent
    this(file, Charset.defaultCharset());
  }

  public SimpleStringPersistentEnumerator(final @NotNull Path file,
                                          final @NotNull Charset charset) {
    this.myFile = file;
    this.charset = charset;

    final Pair<Object2IntMap<String>, Int2ObjectMap<String>> pair = readStorageFromDisk(file, charset);
    myInvertedState = pair.getFirst();
    myForwardState = pair.getSecond();
  }

  public @NotNull Path getFile() {
    return myFile;
  }

  @Override
  public synchronized int enumerate(@Nullable String value) {
    int id = myInvertedState.getInt(value);
    if (id != myInvertedState.defaultReturnValue()) {
      return id;
    }

    if (value != null && StringUtil.containsLineBreak(value)) {
      throw new IllegalArgumentException("SimpleStringPersistentEnumerator doesn't support multi-line strings: [" + value + "]");
    }

    // do not use myInvertedState.size because enumeration file may have duplicates on different lines
    id = myForwardState.size() + 1;
    myInvertedState.put(value, id);
    myForwardState.put(id, value);
    writeStorageToDisk(myForwardState, myFile, charset);
    return id;
  }

  public synchronized @NotNull Collection<String> entries() {
    return new ArrayList<>(myInvertedState.keySet());
  }

  public synchronized @NotNull Map<String, Integer> getInvertedState() {
    return Collections.unmodifiableMap(myInvertedState);
  }

  @Override
  @Nullable
  public synchronized String valueOf(int idx) {
    return myForwardState.get(idx);
  }

  public synchronized void forceDiskSync() {
    writeStorageToDisk(myForwardState, myFile, charset);
  }

  public synchronized boolean isEmpty() {
    return myInvertedState.isEmpty();
  }

  public synchronized int getSize() {
    return myInvertedState.size();
  }

  @NotNull
  public String dumpToString() {
    return myInvertedState
      .object2IntEntrySet()
      .stream()
      .sorted(Comparator.comparing(e -> e.getIntValue()))
      .map(e -> e.getKey() + "->" + e.getIntValue()).collect(Collectors.joining("\n"));
  }

  private static @NotNull Pair<Object2IntMap<String>, Int2ObjectMap<String>> readStorageFromDisk(final @NotNull Path file,
                                                                                                 final Charset charset) {
    try {
      final Object2IntMap<String> nameToIdRegistry = new Object2IntOpenHashMap<>();
      final Int2ObjectMap<String> idToNameRegistry = new Int2ObjectOpenHashMap<>();
      final List<String> lines = Files.readAllLines(file, charset);
      for (int i = 0; i < lines.size(); i++) {
        final String name = lines.get(i);
        final int id = i + 1;
        nameToIdRegistry.put(name, id);
        idToNameRegistry.put(id, name);
      }
      return Pair.create(nameToIdRegistry, idToNameRegistry);
    }
    catch (IOException e) {
      writeStorageToDisk(Int2ObjectMaps.emptyMap(), file, charset);
      return Pair.create(new Object2IntOpenHashMap<>(), new Int2ObjectOpenHashMap<>());
    }
  }

  private static void writeStorageToDisk(final @NotNull Int2ObjectMap<String> forwardIndex,
                                         final @NotNull Path file,
                                         final Charset charset) {
    try {
      final String[] names = new String[forwardIndex.size()];
      for (ObjectIterator<Int2ObjectMap.Entry<String>> iterator = Int2ObjectMaps.fastIterator(forwardIndex); iterator.hasNext(); ) {
        final Int2ObjectMap.Entry<String> entry = iterator.next();
        names[entry.getIntKey() - 1] = entry.getValue();
      }

      Files.createDirectories(file.getParent());
      Files.write(file, Arrays.asList(names), charset);
    }
    catch (IOException e) {
      throw new UncheckedIOException("Can't store enumerator to "+file, e);
    }
  }
}
