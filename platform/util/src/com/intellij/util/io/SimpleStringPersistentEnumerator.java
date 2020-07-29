// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import gnu.trove.TObjectIntHashMap;
import gnu.trove.TObjectIntProcedure;
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
  private final TObjectIntHashMap<String> myState;

  public SimpleStringPersistentEnumerator(@NotNull Path file) {
    myFile = file;
    myState = new TObjectIntHashMap<>();

    readStorageFromDisk();
  }

  public synchronized short enumerate(@Nullable String value) {
    if (myState.containsKey(value)) {
      return (short) myState.get(value);
    }

    int n = myState.size() + 1;
    assert n <= MAX_NUMBER_OF_INDICES : "Number of indices exceeded: "+n;

    myState.put(value, n);
    writeStorageToDisk();
    return (short)n;
  }

  @Nullable
  public synchronized String valueOf(short idx) {
    String[] result = {null};
    myState.forEachEntry(new TObjectIntProcedure<String>() {
      @Override
      public boolean execute(String data, int dataId) {
        if (dataId == idx) {
          result[0] = data;
          return false;
        }
        return true;
      }
    });
    return result[0];
  }

  public synchronized void forceDiskSync() {
    writeStorageToDisk();
  }
  
  private synchronized void readStorageFromDisk() {
    try {
      TObjectIntHashMap<String> nameToIdRegistry = new TObjectIntHashMap<>();
      List<String> lines = Files.readAllLines(myFile, Charset.defaultCharset());
      for (int i = 0; i < lines.size(); i++) {
        String name = lines.get(i);
        nameToIdRegistry.put(name, i + 1);
      }

      myState.ensureCapacity(nameToIdRegistry.size());
      nameToIdRegistry.forEachEntry((name, index) -> {
        myState.put(name, index);
        return true;
      });
    }
    catch (IOException e) {
      myState.clear();
      writeStorageToDisk();
    }
  }
  
  private void writeStorageToDisk() {
    try {
      final String[] names = new String[myState.size()];
      myState.forEachEntry((key, value) -> {
        names[value - 1] = key;
        return true;
      });

      Files.createDirectories(myFile.getParent());
      Files.write(myFile, Arrays.asList(names), Charset.defaultCharset());
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
