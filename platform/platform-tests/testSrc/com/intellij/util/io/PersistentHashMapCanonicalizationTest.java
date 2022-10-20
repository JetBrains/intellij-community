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

/**
 * IDEA-303801: create 'reproducible'/canonical version of {@linkplain PersistentHashMap}
 */
@RunWith(Parameterized.class)
public class PersistentHashMapCanonicalizationTest {
  public static final int KEYS_COUNT = 100_000;
  public static final int ENOUGH_SHUFFLE_TRIES = 32;

  public static final int MAX_KEY_VALUE_SIZE = 50;

  @Rule
  public final TemporaryFolder tmpDirectory = new TemporaryFolder();

  private final Function<List<String>, List<String>> sorter;

  public PersistentHashMapCanonicalizationTest(final Function<List<String>, List<String>> sorter) { this.sorter = sorter; }

  private static List<String> stableSortByStringCompare(final List<String> keys) {
    final List<String> keysCopy = new ArrayList<>(keys);
    Collections.sort(keysCopy, Comparator.naturalOrder());
    return keysCopy;
  }


  public static <K> List<K> stableSortBySerializedBytes(final List<K> keys,
                                                        final DataExternalizer<K> externalizer) {
    return keys.stream()
      .map(key -> {
        try {
          final ByteArrayOutputStream bos = new ByteArrayOutputStream();
          final java.io.DataOutputStream dos = new DataOutputStream(bos);
          externalizer.save(dos, key);
          dos.close();
          final byte[] serializedKey = bos.toByteArray();
          return Pair.pair(key, serializedKey);
        }
        catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      })
      .sorted((o1, o2) -> Arrays.compare(o1.second, o2.second))
      .map(pair -> pair.first)
      .collect(toList());
  }

  public static <K> List<K> stableSortByHashCode(final List<K> keys,
                                                 final KeyDescriptor<K> descriptor) {
    return keys.stream()
      .sorted(Comparator.comparingInt(descriptor::getHashCode))
      .collect(toList());
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
      }

      //new Object[]{
      //  new Function<List<String>, List<String>>() {
      //    @Override
      //    public List<String> apply(List<String> keys) {
      //      //FIXME RC: sorting by hashcode is potentially much faster than by byte[], but it doesn't
      //      //          provide stable sort -- hash-collided keys are sorted randomly. Hence in its
      //      //          current form it is not suitable. If we could invent a way to resolve collisions
      //      //          it could be a solution again
      //      return stableSortByHashCode(keys, EnumeratorStringDescriptor.INSTANCE);
      //    }
      //
      //    @Override
      //    public String toString() {
      //      return "SortByKeyHashCodes";
      //    }
      //  }
      //}
    );
  }

  @Test
  public void sorterProvidesStableSort() {
    final Map<String, String> keysValues = generateKeyValues(KEYS_COUNT, MAX_KEY_VALUE_SIZE);
    final List<String> keys = keysValues.keySet().stream().toList();

    final List<List<String>> sortedKeys = new ArrayList<>();
    for (int i = 0; i < ENOUGH_SHUFFLE_TRIES; i++) {
      final List<String> keysCopy = new ArrayList<>(keys);
      Collections.shuffle(keysCopy);
      final List<String> sorted = sorter.apply(keysCopy);
      sortedKeys.add(sorted);
    }
    assertEquals(
      1,
      new HashSet<>(sortedKeys).size()
    );
  }

  @Test
  public void canonicalizedMapIsInvariantOnKeyValueAppendingOrder() throws IOException {
    final Map<String, String> keysValues = generateKeyValues(KEYS_COUNT, MAX_KEY_VALUE_SIZE);
    final List<String> keys = keysValues.keySet().stream().toList();

    final List<String> canonicalMapsContentHashes = new ArrayList<>();
    for (int i = 0; i < ENOUGH_SHUFFLE_TRIES; i++) {
      final List<String> keysCopy = new ArrayList<>(keys);
      Collections.shuffle(keysCopy);
      try (final PersistentHashMap<String, String> map = createPHMap()) {
        for (String key : keysCopy) {
          final String value = keysValues.get(key);
          map.put(key, value);
        }
        final PersistentHashMap<String, String> canonicalMap = canonicalize(map, sorter);
        final String hash = hashOfContent(canonicalMap);
        canonicalMapsContentHashes.add(hash);
      }
    }

    assertEquals(
      "All content hashes must be the same",
      1,
      new HashSet<>(canonicalMapsContentHashes).size()
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

  private PersistentHashMap<String, String> canonicalize(
    final @NotNull PersistentHashMap<String, String> originalMap,
    final @NotNull Function<List<String>, List<String>> stableSorter) throws IOException {
    //'Canonical' version of PersistentMap is the map with the same key-values, but added in strict
    // deterministic order (natural string order in this case).
    final PersistentHashMap<String, String> canonicalMap = createPHMap();
    return PersistentHashMap.canonicalize(originalMap, canonicalMap, stableSorter);
  }

  private PersistentMapBase<String, String> canonicalize(
    final @NotNull PersistentMapBase<String, String> originalMap,
    final @NotNull Function<List<String>, List<String>> stableSorter) throws IOException {
    //'Canonical' version of PersistentMap is the map with the same key-values, but added in strict
    // deterministic order (natural string order in this case).
    final PersistentMapBase<String, String> canonicalMap = createPHMapBase();
    return PersistentMapBase.canonicalize(originalMap, canonicalMap, stableSorter);
  }

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

  private PersistentMapBase<String, String> createPHMapBase() throws IOException {
    final PersistentHashMap<String, String> map = createPHMap();
    return stealTheImpl(map);
  }

  @SuppressWarnings("unchecked")
  private static PersistentMapBase<String, String> stealTheImpl(final PersistentHashMap<String, String> map) {
    try {
      final Field impl = map.getClass().getDeclaredField("myImpl");
      impl.setAccessible(true);
      return (PersistentMapBase<String, String>)impl.get(map);
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
