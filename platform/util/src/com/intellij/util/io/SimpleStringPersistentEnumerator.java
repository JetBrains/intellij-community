// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import it.unimi.dsi.fastutil.objects.Object2ShortMap;
import it.unimi.dsi.fastutil.objects.Object2ShortMaps;
import it.unimi.dsi.fastutil.objects.Object2ShortOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Simple version of string enumerator:
 * <p><ul>
 * <li>strings are stored directly in UTF-8 encoding
 * <li>always has synchronized state between disk and memory state
 * <li>has limited size by {@link Short#MAX_VALUE}
 * <li>{@link SimpleStringPersistentEnumerator#valueOf(short)} has O(n) complexity where n is enumerator size
 * </ul>
 */
@ApiStatus.Internal
public final class SimpleStringPersistentEnumerator {
  public static final int MAX_NUMBER_OF_INDICES = Short.MAX_VALUE;

  @NotNull
  private final Path myFile;
  @NotNull
  private final Object2ShortOpenHashMap<String> myState;

  public SimpleStringPersistentEnumerator(@NotNull Path file) {
    myFile = file;
    myState = readStorageFromDisk(file);
  }

  public synchronized short enumerate(@Nullable String value) {
    if (myState.containsKey(value)) {
      return myState.getShort(value);
    }

    int n = myState.size() + 1;
    assert n <= MAX_NUMBER_OF_INDICES : "Number of indices exceeded: "+n;

    myState.put(value, (short)n);
    writeStorageToDisk(myState, myFile);
    return (short)n;
  }

  @Nullable
  public synchronized String valueOf(short idx) {
    for (ObjectIterator<Object2ShortMap.Entry<String>> iterator = myState.object2ShortEntrySet().fastIterator(); iterator.hasNext(); ) {
      Object2ShortMap.Entry<String> entry = iterator.next();
      if (entry.getShortValue() == idx) {
        return entry.getKey();
      }
    }
    return null;
  }

  public synchronized void forceDiskSync() {
    writeStorageToDisk(myState, myFile);
  }

  private static @NotNull Object2ShortOpenHashMap<String> readStorageFromDisk(@NotNull Path file) {
    try {
      Object2ShortOpenHashMap<String> nameToIdRegistry = new Object2ShortOpenHashMap<>();
      List<String> lines = Files.readAllLines(file, Charset.defaultCharset());
      for (int i = 0; i < lines.size(); i++) {
        String name = lines.get(i);
        nameToIdRegistry.put(name, (short)(i + 1));
      }
      return nameToIdRegistry;
    }
    catch (IOException e) {
      writeStorageToDisk(Object2ShortMaps.emptyMap(), file);
      return new Object2ShortOpenHashMap<>();
    }
  }

  private static void writeStorageToDisk(@NotNull Object2ShortMap<String> state, @NotNull Path file) {
    try {
      String[] names = new String[state.size()];
      for (ObjectIterator<Object2ShortMap.Entry<String>> iterator = Object2ShortMaps.fastIterator(state); iterator.hasNext(); ) {
        Object2ShortMap.Entry<String> entry = iterator.next();
        names[entry.getShortValue() - 1] = entry.getKey();
      }

      Files.createDirectories(file.getParent());
      Files.write(file, Arrays.asList(names), Charset.defaultCharset());
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
