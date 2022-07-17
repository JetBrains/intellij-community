// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Simple version of string enumerator:
 * <p><ul>
 * <li>strings are stored directly in UTF-8 encoding
 * <li>always has synchronized state between disk and memory state
 * </ul>
 */
@ApiStatus.Internal
public final class SimpleStringPersistentEnumerator implements DataEnumerator<String> {
  private final @NotNull Path myFile;
  private final @NotNull Object2IntMap<String> myInvertedState;
  private final @NotNull Int2ObjectMap<String> myForwardState;

  public SimpleStringPersistentEnumerator(@NotNull Path file) {
    myFile = file;
    Pair<Object2IntMap<String>, Int2ObjectMap<String>> pair = readStorageFromDisk(file);
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
      throw new RuntimeException("SimpleStringPersistentEnumerator doesn't support multi-line strings");
    }

    int n = myInvertedState.size() + 1;
    myInvertedState.put(value, n);
    myForwardState.put(n, value);
    writeStorageToDisk(myForwardState, myFile);
    return n;
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
    writeStorageToDisk(myForwardState, myFile);
  }

  public synchronized boolean isEmpty() {
    return myInvertedState.isEmpty();
  }

  @NotNull
  public String dumpToString() {
    return myInvertedState
      .object2IntEntrySet()
      .stream()
      .sorted(Comparator.comparing(e -> e.getIntValue()))
      .map(e -> e.getKey() + "->" + e.getIntValue()).collect(Collectors.joining("\n"));
  }

  private static @NotNull Pair<Object2IntMap<String>, Int2ObjectMap<String>> readStorageFromDisk(@NotNull Path file) {
    try {
      Object2IntMap<String> nameToIdRegistry = new Object2IntOpenHashMap<>();
      Int2ObjectMap<String> idToNameRegistry = new Int2ObjectOpenHashMap<>();
      List<String> lines = Files.readAllLines(file, Charset.defaultCharset());
      for (int i = 0; i < lines.size(); i++) {
        String name = lines.get(i);
        nameToIdRegistry.put(name, i + 1);
        idToNameRegistry.put(i + 1, name);
      }
      return Pair.create(nameToIdRegistry, idToNameRegistry);
    }
    catch (IOException e) {
      writeStorageToDisk(Int2ObjectMaps.emptyMap(), file);
      return Pair.create(new Object2IntOpenHashMap<>(), new Int2ObjectOpenHashMap<>());
    }
  }

  private static void writeStorageToDisk(@NotNull Int2ObjectMap<String> forwardIndex, @NotNull Path file) {
    try {
      String[] names = new String[forwardIndex.size()];
      for (ObjectIterator<Int2ObjectMap.Entry<String>> iterator = Int2ObjectMaps.fastIterator(forwardIndex); iterator.hasNext(); ) {
        Int2ObjectMap.Entry<String> entry = iterator.next();
        names[entry.getIntKey() - 1] = entry.getValue();
      }

      Files.createDirectories(file.getParent());
      Files.write(file, Arrays.asList(names), Charset.defaultCharset());
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
