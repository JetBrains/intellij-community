// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.Assert.assertEquals;

/**
 * IDEA-303801: create 'reproducible'/canonical version of {@linkplain PersistentHashMap}
 */
public class PersistentHashMapCanonicalizationTest {
  public static final int KEYS_COUNT = 1_000;
  public static final int MAX_KEY_VALUE_SIZE = 50;

  @Rule
  public final TemporaryFolder tmpDirectory = new TemporaryFolder();

  @Test
  public void canonicalizedMapIsInvariantOnKeyValueAppendingOrder() throws IOException {
    final Map<String, String> keysValues = generateKeyValues(KEYS_COUNT, MAX_KEY_VALUE_SIZE);
    final List<String> keys = keysValues.keySet().stream().toList();

    final List<String> canonicalMapsContentHashes = new ArrayList<>();
    for (int i = 0; i < 16; i++) {
      final ArrayList<String> keysCopy = new ArrayList<>(keys);
      Collections.shuffle(keysCopy);
      try (final PersistentHashMap<String, String> map = createPHMap()) {
        for (String key : keysCopy) {
          final String value = keysValues.get(key);
          map.put(key, value);
        }
        final PersistentHashMap<String, String> canonicalMap = canonicalize(map);
        final String hash = hashOfContent(canonicalMap);
        canonicalMapsContentHashes.add(hash);
      }
    }

    assertEquals(
      "All content hashes must be the same",
      new HashSet<>(canonicalMapsContentHashes).size(),
      1
    );
  }

  @After
  public void tearDown() throws Exception {
    for (PersistentHashMap<?, ?> map : mapsEntries.keySet()) {
      if(!map.isClosed()){
        map.close();
      }
    }
  }

  /* ============================ infrastructure: ===================================== */

  private static final class PHMEntry {
    private final @NotNull File directory;

    private PHMEntry(final @NotNull File directory) { this.directory = directory; }
  }

  private final Map<PersistentHashMap<?, ?>, PHMEntry> mapsEntries = new HashMap<>();

  @NotNull
  private PersistentHashMap<String, String> createPHMap() throws IOException {
    //RC: PHM uses >1 file to store data, but doesn't provide methods to get all files it is used. Hence,
    //    the workaround here: create dedicated folder for each PHM, and treat all files in that folder
    //    as apt PHM content
    final File folder = tmpDirectory.newFolder();
    final File mainFile = new File(folder, "map");
    final PersistentHashMap<String, String> persistentMap = new PersistentHashMap<>(
      mainFile,
      EnumeratorStringDescriptor.INSTANCE,
      EnumeratorStringDescriptor.INSTANCE
    );
    mapsEntries.put(persistentMap, new PHMEntry(folder));
    return persistentMap;
  }

  private String hashOfContent(final PersistentHashMap<String, String> map) throws IOException {
    map.force();
    final PHMEntry entry = mapsEntries.get(map);
    assert entry != null : "No entry for " + map + ": only maps created by .createPHMap() are allowed";
    final StringBuilder sb = new StringBuilder();
    for (File file : entry.directory.listFiles()) {
      sb.append(file.getName())
        .append(": ")
        .append(DigestUtil.sha256Hex(file.toPath()))
        .append('\n');
    }
    return sb.toString();
  }

  private PersistentHashMap<String, String> canonicalize(final PersistentHashMap<String, String> originalMap) throws IOException {
    //'Canonical' version of PersistentMap is the map with the same key-values, but added in strict
    // deterministic order (natural string order in this case).
    final List<String> keys = new ArrayList<>();
    originalMap.processKeysWithExistingMapping(keys::add);
    keys.sort(Comparator.naturalOrder());
    final PersistentHashMap<String, String> canonicalMap = createPHMap();
    for (String key : keys) {
      final String value = originalMap.get(key);
      canonicalMap.put(key, value);
    }
    return canonicalMap;
  }

  private static Map<String, String> generateKeyValues(final int keysCount,
                                                       final int maxKeyValueSize) {
    final var keyValues = new Object2ObjectOpenHashMap<String, String>(keysCount);
    final ThreadLocalRandom rnd = ThreadLocalRandom.current();
    for (int i = 0; i < keysCount; i++) {
      final String key = randomAlphanumericString(rnd, rnd.nextInt(maxKeyValueSize));
      final String value = randomAlphanumericString(rnd, rnd.nextInt(maxKeyValueSize));
      keyValues.put(key, value);
    }
    return keyValues;
  }

  @NotNull
  protected static String randomAlphanumericString(final ThreadLocalRandom rnd,
                                                   final int size) {
    final char[] chars = new char[size];
    final int base = 36;
    for (int i = 0; i < chars.length; i++) {
      chars[i] = Character.forDigit(rnd.nextInt(0, base), base);
    }
    return new String(chars);
  }
}
