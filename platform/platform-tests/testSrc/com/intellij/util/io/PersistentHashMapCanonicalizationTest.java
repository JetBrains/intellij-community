// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.openapi.util.Pair;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.*;
import java.io.DataOutputStream;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * IDEA-303801: create 'reproducible'/canonical version of {@linkplain PersistentHashMap}
 */
@RunWith(Parameterized.class)
public class PersistentHashMapCanonicalizationTest {
  public static final int KEYS_COUNT = 10_101;
  public static final int ENOUGH_SHUFFLE_TRIES = 32;

  public static final int MAX_KEY_VALUE_SIZE = 50;

  @Rule
  public final TemporaryFolder tmpDirectory = new TemporaryFolder();

  private final Function<? super List<String>, ? extends List<String>> sorter;

  public PersistentHashMapCanonicalizationTest(final Function<? super List<String>, ? extends List<String>> sorter) { this.sorter = sorter; }

  private static List<String> stableSortByStringCompare(final List<String> keys) {
    final List<String> keysCopy = new ArrayList<>(keys);
    Collections.sort(keysCopy, Comparator.naturalOrder());
    return keysCopy;
  }


  public static <K> List<K> stableSortBySerializedBytes(final List<? extends K> keys,
                                                        final DataExternalizer<K> externalizer) {
    return keys.stream()
      .map(key -> new Pair<>(key, keyToBytes(externalizer, key)))
      .sorted((o1, o2) -> Arrays.compare(o1.second, o2.second))
      .map(pair -> pair.first)
      .collect(toList());
  }

  public static <K> List<K> stableSortByHashCodeAndBytes(final List<K> keys,
                                                         final KeyDescriptor<K> descriptor) {
    //RC: sorting keys by serialized bytes could be demanding (CPU/memory-wise), sorting by hash is much
    //    cheaper. But hash-collisions make such a sort unstable -- keys with same hash are 'unordered'
    //    We mitigate it by sorting such (collided) keys by serializedBytes, while non-colliding
    //    keys by hash -- should be still much more effective than pure stableSortBySerializedBytes()
    return keys.stream()
      .sorted((k1, k2) -> {
        final int k1Hash = descriptor.getHashCode(k1);
        final int k2Hash = descriptor.getHashCode(k1);
        if (k1Hash != k2Hash) {
          return Integer.compare(k1Hash, k2Hash);
        }
        else {
          final byte[] k1Bytes = keyToBytes(descriptor, k1);
          final byte[] k2Bytes = keyToBytes(descriptor, k2);
          return Arrays.compare(k1Bytes, k2Bytes);
        }
      }).toList();
  }

  private static <K> byte[] keyToBytes(final @NotNull DataExternalizer<K> externalizer,
                                       final K key) {
    try {
      final ByteArrayOutputStream bos = new ByteArrayOutputStream();
      final DataOutputStream dos = new DataOutputStream(bos);
      externalizer.save(dos, key);
      dos.close();
      return bos.toByteArray();
    }
    catch (IOException e) {
      throw new UncheckedIOException("Can't serialize key: " + key, e);
    }
  }

  @Parameterized.Parameters(name = "{0}")
  public static List<Object[]> sorters() {
    return Arrays.asList(
      new Object[]{
        new Function<List<String>, List<String>>() {
          @Override
          public List<String> apply(List<String> keys) {
            return stableSortByStringCompare(keys);
          }

          @Override
          public String toString() {
            return "SortByStringCompare";
          }
        }
      },
      new Object[]{
        new Function<List<String>, List<String>>() {
          @Override
          public List<String> apply(List<String> keys) {
            return stableSortBySerializedBytes(keys, EnumeratorStringDescriptor.INSTANCE);
          }

          @Override
          public String toString() {
            return "SortBySerializedBytes";
          }
        }
      },

      new Object[]{
        new Function<List<String>, List<String>>() {
          @Override
          public List<String> apply(List<String> keys) {
            return stableSortByHashCodeAndBytes(keys, EnumeratorStringDescriptor.INSTANCE);
          }

          @Override
          public String toString() {
            return "SortByKeyHashCodesAndBytes";
          }
        }
      }
    );
  }

  @Test
  public void sorterProvidesStableSort() {
    final Map<String, String> keysValues = generateKeyValues(KEYS_COUNT, MAX_KEY_VALUE_SIZE);
    final List<String> keys = keysValues.keySet().stream().toList();

    final List<List<? extends String>> sortedKeys = new ArrayList<>();
    for (int i = 0; i < ENOUGH_SHUFFLE_TRIES; i++) {
      final List<String> keysCopy = new ArrayList<>(keys);
      Collections.shuffle(keysCopy);
      final List<String> sorted = sorter.apply(keysCopy);
      sortedKeys.add(sorted);
    }

    assertEquals(
      "Stable sort must produce same sorted list, regardless of initial order of items",
      1,
      new HashSet<>(sortedKeys).size()
    );
  }

  @Test
  public void canonicalizedMapIsInvariantOnKeyValueAppendingOrder_StringKeys() throws IOException {
    final Map<String, String> keysValues = generateKeyValues(KEYS_COUNT, MAX_KEY_VALUE_SIZE);
    final List<String> keys = keysValues.keySet().stream().toList();

    final List<String> canonicalMapsContentHashes = new ArrayList<>();
    final List<String> originalMapsContentHashes = new ArrayList<>();
    for (int i = 0; i < ENOUGH_SHUFFLE_TRIES; i++) {
      final List<String> shuffledKeys = new ArrayList<>(keys);
      Collections.shuffle(shuffledKeys);
      try (final PersistentHashMap<String, String> map = createPHMap(EnumeratorStringDescriptor.INSTANCE,
                                                                     EnumeratorStringDescriptor.INSTANCE)) {
        for (String key : shuffledKeys) {
          final String value = keysValues.get(key);
          map.put(key, value);
        }
        originalMapsContentHashes.add(hashOfContent(map));
        final PersistentHashMap<String, String> canonicalMap = canonicalize(map, sorter);
        canonicalMapsContentHashes.add(hashOfContent(canonicalMap));
      }
    }

    assertEquals(
      "All content hashes must be the same for canonical maps",
      1,
      new HashSet<>(canonicalMapsContentHashes).size()
    );
    assertNotEquals(
      "Content expected to be different for original maps",
      1,
      new HashSet<>(originalMapsContentHashes).size()
    );
  }

  @After
  public void tearDown() throws Exception {
    for (PersistentHashMap<?, ?> map : mapsEntries.keySet()) {
      if (!map.isClosed()) {
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

  private String hashOfContent(final PersistentHashMap<String, String> map) {
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

  private <K, V> PersistentHashMap<K, V> canonicalize(
    final @NotNull PersistentHashMap<K, V> originalMap,
    final @NotNull Function<? super List<K>, ? extends List<K>> stableSorter) throws IOException {
    //'Canonical' version of PersistentMap is the map with the same key-values, but added in strict
    // deterministic order (natural string order in this case).
    final PersistentMapBase<K, V> mapImpl = originalMap.getImpl();
    final KeyDescriptor<K> keysDescriptor = ((PersistentMapImpl<K, V>)mapImpl).getKeyDescriptor();
    final DataExternalizer<V> valuesExternalizer = mapImpl.getValuesExternalizer();
    final PersistentHashMap<K, V> canonicalMap = createPHMap(
      keysDescriptor,
      valuesExternalizer
    );
    return PersistentHashMap.canonicalize(originalMap, canonicalMap, stableSorter, v -> v);
  }

  @NotNull
  private <K, V> PersistentHashMap<K, V> createPHMap(final @NotNull KeyDescriptor<K> keyDescriptor,
                                                     final @NotNull DataExternalizer<V> valueDescriptor) throws IOException {
    //RC: PHM uses >1 file to store data, but doesn't provide methods to get all files it is used. Hence,
    //    the workaround here: create dedicated folder for each PHM, and treat all files in that folder
    //    as apt PHM content
    final File folder = tmpDirectory.newFolder();
    final File mainFile = new File(folder, "map");
    final PersistentHashMap<K, V> persistentMap = new PersistentHashMap<>(
      mainFile,
      keyDescriptor,
      valueDescriptor
    );
    mapsEntries.put(persistentMap, new PHMEntry(folder));
    return persistentMap;
  }

  private <K, V> PersistentMapBase<K, V> createPHMapBase(final @NotNull KeyDescriptor<K> keyDescriptor,
                                                         final @NotNull DataExternalizer<V> valueExternalizer) throws IOException {
    final PersistentHashMap<K, V> map = createPHMap(keyDescriptor, valueExternalizer);
    return stealTheImpl(map);
  }

  @SuppressWarnings("unchecked")
  private static <K, V> PersistentMapBase<K, V> stealTheImpl(final PersistentHashMap<K, V> map) {
    try {
      final Field impl = map.getClass().getDeclaredField("myImpl");
      impl.setAccessible(true);
      return (PersistentMapBase<K, V>)impl.get(map);
    }
    catch (Throwable t) {
      throw new AssertionError("Can't steal PersistentHashMap.myImpl field", t);
    }
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
