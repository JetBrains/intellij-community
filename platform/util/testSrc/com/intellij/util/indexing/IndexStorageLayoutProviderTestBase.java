// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSRecordsStorage;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.indexing.IndexStorageLayoutProviderTestBase.MocksBuildingBlocks.KeysGenerator;
import com.intellij.util.indexing.impl.IndexDebugProperties;
import com.intellij.util.indexing.impl.IndexStorage;
import com.intellij.util.indexing.impl.InputData;
import com.intellij.util.indexing.impl.InputDataDiffBuilder;
import com.intellij.util.indexing.impl.forward.ForwardIndex;
import com.intellij.util.indexing.impl.forward.ForwardIndexAccessor;
import com.intellij.util.indexing.storage.FileBasedIndexLayoutProvider;
import com.intellij.util.indexing.storage.sharding.ShardableIndexExtension;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorIntegerDescriptor;
import com.intellij.util.io.KeyDescriptor;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Base class to test {@link FileBasedIndexLayoutProvider} implementations: it generates fake but realistic data for index
 * to store, and check basic invariants.
 * <p>
 * To use: subclass and provide the implementation to test in base class ctor.
 * <p>
 * To customize setup to test on: see {@link #defaultSetupsToTest()} docs
 */
@TestApplication//SingleEntry indexes need application
public abstract class IndexStorageLayoutProviderTestBase {
  private static boolean wasInStressTest;

  protected final int inputsCountToTestWith;

  protected final @NotNull FileBasedIndexLayoutProvider storageLayoutProviderToTest;

  protected IndexStorageLayoutProviderTestBase(@NotNull FileBasedIndexLayoutProvider providerToTest,
                                               int inputsCountToTestWith) {
    this.storageLayoutProviderToTest = providerToTest;
    this.inputsCountToTestWith = inputsCountToTestWith;
  }

  @ParameterizedTest
  @ArgumentsSource(Setups.SetupsToTestProvider.class)
  public <K, V> void inputs_Added_IntoIndexStorage_couldBeReadBackAsIs(
    @NotNull FileBasedIndexExtension<K, V> extension,
    @NotNull InputDataGenerator<K, V> inputDataGenerator) throws Exception {

    long[] inputSubstrates = inputDataGenerator.generateInputSubstrate()
      .limit(inputsCountToTestWith)
      .toArray();

    var storageLayout = storageLayoutProviderToTest.getLayout(extension, Collections.emptyList());
    storageLayout.clearIndexData();

    try (IndexStorage<K, V> indexStorage = storageLayout.openIndexStorage()) {
      for (long substrate : inputSubstrates) {
        var input = inputDataGenerator.unpackSubstrate(substrate);
        int inputId = input.inputId();

        Map<K, V> inputData = input.inputData();
        for (Map.Entry<K, V> e : inputData.entrySet()) {
          indexStorage.addValue(e.getKey(), inputId, e.getValue());
        }
      }

      LimitedErrorsList<String> inconsistencies = new LimitedErrorsList<>(64);
      for (long substrate : inputSubstrates) {
        var input = inputDataGenerator.unpackSubstrate(substrate);
        int expectedInputId = input.inputId;
        Map<K, V> inputData = input.inputData;

        for (Map.Entry<K, V> e : inputData.entrySet()) {
          K expectedKey = e.getKey();
          V expectedValue = e.getValue();
          boolean[] foundRef = {false};
          indexStorage.read(expectedKey, container -> {
            if (contains(container, expectedValue, expectedInputId)) {
              foundRef[0] = true;
              return false;
            }
            return true;
          });
          if (!foundRef[0]) {
            inconsistencies.add(
              "container(" + expectedKey + ") must contain inputId(=" + expectedInputId + ") with value(=" + expectedValue + ")"
            );
          }
        }
      }

      assertTrue(
        inconsistencies.isEmpty(),
        () -> inconsistencies.dump()
      );
    }
    finally {
      storageLayout.clearIndexData();
    }
  }

  @ParameterizedTest
  @ArgumentsSource(Setups.SetupsToTestProvider.class)
  public <K, V> void inputs_Added_IntoIndexStorage_couldBeReadBackAsIs_afterIndexStorageReopened(
    @NotNull FileBasedIndexExtension<K, V> extension,
    @NotNull InputDataGenerator<K, V> inputDataGenerator) throws Exception {

    long[] inputSubstrates = inputDataGenerator.generateInputSubstrate()
      .limit(inputsCountToTestWith)
      .toArray();

    var storageLayout = storageLayoutProviderToTest.getLayout(extension, Collections.emptyList());
    storageLayout.clearIndexData();

    try (IndexStorage<K, V> indexStorage = storageLayout.openIndexStorage()) {
      for (long substrate : inputSubstrates) {
        var input = inputDataGenerator.unpackSubstrate(substrate);
        int inputId = input.inputId();

        Map<K, V> inputData = input.inputData();
        for (Map.Entry<K, V> e : inputData.entrySet()) {
          indexStorage.addValue(e.getKey(), inputId, e.getValue());
        }
      }
    }

    LimitedErrorsList<String> inconsistencies = new LimitedErrorsList<>(64);
    //re-open:
    try (IndexStorage<K, V> indexStorage = storageLayout.openIndexStorage()) {
      for (long substrate : inputSubstrates) {
        var input = inputDataGenerator.unpackSubstrate(substrate);
        int expectedInputId = input.inputId;
        Map<K, V> inputData = input.inputData;

        for (Map.Entry<K, V> e : inputData.entrySet()) {
          K expectedKey = e.getKey();
          V expectedValue = e.getValue();
          boolean[] foundRef = {false};
          indexStorage.read(expectedKey, container -> {
            if (contains(container, expectedValue, expectedInputId)) {
              foundRef[0] = true;
              return false;
            }
            return true;
          });
          if (!foundRef[0]) {
            inconsistencies.add(
              "container(" + expectedKey + ") must contain inputId(=" + expectedInputId + ") with value(=" + expectedValue + ")");
          }
        }
      }

      assertTrue(
        inconsistencies.isEmpty(),
        () -> inconsistencies.dump()
      );
    }
    finally {
      storageLayout.clearIndexData();
    }
  }

  @ParameterizedTest
  @ArgumentsSource(Setups.SetupsToTestProvider.class)
  public <K, V> void inputs_AddedAndRemoved_FromIndexStorage_nothingCouldBeReadBackAsIs(
    @NotNull FileBasedIndexExtension<K, V> extension,
    @NotNull InputDataGenerator<K, V> inputDataGenerator) throws Exception {

    long[] inputSubstrates = inputDataGenerator.generateInputSubstrate()
      .limit(inputsCountToTestWith)
      .toArray();

    var storageLayout = storageLayoutProviderToTest.getLayout(extension, Collections.emptyList());
    storageLayout.clearIndexData();

    try (IndexStorage<K, V> indexStorage = storageLayout.openIndexStorage()) {
      //add:
      for (long substrate : inputSubstrates) {
        var input = inputDataGenerator.unpackSubstrate(substrate);
        int inputId = input.inputId();

        Map<K, V> inputData = input.inputData();
        for (Map.Entry<K, V> e : inputData.entrySet()) {
          indexStorage.addValue(e.getKey(), inputId, e.getValue());
        }
      }

      //remove:
      for (long substrate : inputSubstrates) {
        var input = inputDataGenerator.unpackSubstrate(substrate);
        int inputId = input.inputId();

        Map<K, V> inputData = input.inputData();
        for (Map.Entry<K, V> e : inputData.entrySet()) {
          indexStorage.removeAllValues(e.getKey(), inputId);
        }
      }

      //check empty:
      LimitedErrorsList<String> inconsistencies = new LimitedErrorsList<>(64);
      for (long substrate : inputSubstrates) {
        var input = inputDataGenerator.unpackSubstrate(substrate);
        for (Map.Entry<K, V> e : input.inputData.entrySet()) {
          K key = e.getKey();
          indexStorage.read(key, container -> {
            if (container.size() > 0) {
              inconsistencies.add("It must be no entries in container(" + key + "), but: " + dump(container));
            }
            return true;
          });
        }
      }

      assertTrue(
        inconsistencies.isEmpty(),
        () -> inconsistencies.dump()
      );
    }
    finally {
      storageLayout.clearIndexData();
    }
  }


  @ParameterizedTest
  @ArgumentsSource(Setups.SetupsToTestProvider.class)
  public <K, V> void inputs_Added_IntoForwardIndex_couldBeReadBackAsIs(
    @NotNull FileBasedIndexExtension<K, V> extension,
    @NotNull InputDataGenerator<K, V> inputDataGenerator) throws Exception {

    assumeFalse(
      extension instanceof SingleEntryFileBasedIndexExtension,
      "SingleEntryFileBasedIndexExtension doesn't use ForwardIndex"
    );

    long[] inputSubstrates = inputDataGenerator.generateInputSubstrate()
      .limit(inputsCountToTestWith)
      .toArray();

    var storageLayout = storageLayoutProviderToTest.getLayout(extension, Collections.emptyList());
    storageLayout.clearIndexData();

    ForwardIndexAccessor<K, V> forwardIndexAccessor = storageLayout.getForwardIndexAccessor();

    try (ForwardIndex forwardIndex = storageLayout.openForwardIndex()) {
      for (long substrate : inputSubstrates) {
        var input = inputDataGenerator.unpackSubstrate(substrate);

        int inputId = input.inputId();
        InputData<K, V> inputData = input.asInputData();

        ByteArraySequence serializedData = forwardIndexAccessor.serializeIndexedData(inputData);
        forwardIndex.put(inputId, serializedData);
      }

      LimitedErrorsList<String> inconsistencies = new LimitedErrorsList<>(64);
      for (long substrate : inputSubstrates) {
        var input = inputDataGenerator.unpackSubstrate(substrate);
        int inputId = input.inputId;
        Map<K, V> expectedInputData = input.inputData;
        ByteArraySequence serializedData = forwardIndex.get(inputId);
        forwardIndexAccessor.getDiffBuilder(inputId, serializedData).differentiate(
          expectedInputData,
          (kind, k, v, _inputId) -> {
            inconsistencies.add(kind + "(" + k + ", " + v + ")[" + _inputId + "]");
          }
        );
      }

      assertTrue(
        inconsistencies.isEmpty(),
        () -> inconsistencies.dump()
      );
    }
    finally {
      storageLayout.clearIndexData();
    }
  }

  @ParameterizedTest
  @ArgumentsSource(Setups.SetupsToTestProvider.class)
  public <K, V> void inputs_Added_IntoForwardIndex_couldBeReadBackAsIs_afterIndexStorageReopened(
    @NotNull FileBasedIndexExtension<K, V> extension,
    @NotNull InputDataGenerator<K, V> inputDataGenerator) throws Exception {

    assumeFalse(
      extension instanceof SingleEntryFileBasedIndexExtension,
      "SingleEntryFileBasedIndexExtension doesn't use ForwardIndex"
    );

    long[] inputSubstrates = inputDataGenerator.generateInputSubstrate()
      .limit(inputsCountToTestWith)
      .toArray();

    var storageLayout = storageLayoutProviderToTest.getLayout(extension, Collections.emptyList());
    storageLayout.clearIndexData();

    ForwardIndexAccessor<K, V> forwardIndexAccessor = storageLayout.getForwardIndexAccessor();

    try (ForwardIndex forwardIndex = storageLayout.openForwardIndex()) {
      for (long substrate : inputSubstrates) {
        var input = inputDataGenerator.unpackSubstrate(substrate);

        int inputId = input.inputId();
        InputData<K, V> inputData = input.asInputData();

        ByteArraySequence serializedData = forwardIndexAccessor.serializeIndexedData(inputData);
        forwardIndex.put(inputId, serializedData);
      }
    }

    LimitedErrorsList<String> inconsistencies = new LimitedErrorsList<>(64);
    //re-open:
    try (ForwardIndex forwardIndex = storageLayout.openForwardIndex()) {
      for (long substrate : inputSubstrates) {
        var input = inputDataGenerator.unpackSubstrate(substrate);
        int inputId = input.inputId;
        Map<K, V> expectedInputData = input.inputData;
        ByteArraySequence serializedData = forwardIndex.get(inputId);
        InputDataDiffBuilder<K, V> diffBuilder = forwardIndexAccessor.getDiffBuilder(
          inputId,
          serializedData
        );
        diffBuilder.differentiate(
          expectedInputData,
          (kind, key, value, _inputId) -> {
            fail("Shouldn't be any difference, but: " + kind + "(" + key + ", " + value + ")[" + _inputId + "]");
          });
      }

      assertTrue(
        inconsistencies.isEmpty(),
        () -> inconsistencies.dump()
      );
    }
    finally {
      storageLayout.clearIndexData();
    }
  }

  @BeforeAll
  static void disableExpensiveChecks() {
    //disable expensive checks in ValueContainerImpl, since those checks cost too too much memory, and
    // leads to OoM in TeamCity
    wasInStressTest = IndexDebugProperties.IS_IN_STRESS_TESTS;
    IndexDebugProperties.IS_IN_STRESS_TESTS = true;
  }

  @AfterAll
  static void reEnableExpensiveChecks() {
    IndexDebugProperties.IS_IN_STRESS_TESTS = wasInStressTest;
  }

  /** @return true if (expectedValue, expectedInputId) tuple is in the container */
  protected static <V> boolean contains(@NotNull ValueContainer<V> container,
                                        V expectedValue,
                                        int expectedInputId) {
    ValueContainer.ValueIterator<V> it = container.getValueIterator();

    while (it.hasNext()) {
      V value = it.next();
      if (value.equals(expectedValue)) {
        if (it.getValueAssociationPredicate().test(expectedInputId)) {
          return true;
        }
      }
    }
    return false;
  }


  protected static <V> String dump(@NotNull ValueContainer<V> container) {
    ValueContainer.ValueIterator<V> it = container.getValueIterator();
    StringBuilder sb = new StringBuilder("{");
    while (it.hasNext()) {
      V value = it.next();
      sb.append(value).append(": [");
      ValueContainer.IntIterator inputIdsIter = it.getInputIdsIterator();
      while (inputIdsIter.hasNext()) {
        int inputId = inputIdsIter.next();
        sb.append(inputId).append(", ");
      }
      sb.append(value).append("], ");
    }
    sb.append("}");
    return sb.toString();
  }


  /**
   * Generates inputs to test index on.
   * <p>
   * We want to test indexes on huge amount of inputs. To reduce memory occupied by that huge amount of inputs
   * (=reduce chance of OoM), the generation process is split into 2 phases: generation of memory-frugal 'substrates',
   * and unpacking the substrate to the actual {@link Input}.
   * {@link Input} is supposed to be a short-lived object, so GC could quickly collect it, while 'substrates' are
   * expected to share lifespan with the test itself.
   */
  public interface InputDataGenerator<K, V> {
    @NotNull LongStream generateInputSubstrate();

    /** deterministic function of a substrate -- repeated call with the same substrate must produce same Input */
    @NotNull Input<K, V> unpackSubstrate(long substrate);
  }

  public record Input<K, V>(int inputId, @NotNull Map<K, V> inputData) {
    public Input {
      if (inputId <= 0) {
        throw new IllegalArgumentException("inputId(=" + inputId + ") must be >0");
      }
    }

    InputData<K, V> asInputData() {
      return new InputData<>(inputData());
    }
  }

  public static final class MocksBuildingBlocks {
    private MocksBuildingBlocks() {
      throw new AssertionError("Not for instantiation, just a namespace");
    }

    public interface KeysGenerator {
      @NotNull IntStream keys(int seed);
    }
  }

  /** Copied from IdIndex, simplified for testing */
  public static class ManyKeysIntegerToIntegerIndexExtension extends FileBasedIndexExtension<Integer, Integer> {
    public static final @NonNls ID<Integer, Integer> INDEX_ID = ID.create("ManyKeysIntegerToIntegerIndexExtension");

    public static final int VERSION = 42;

    private final int cacheSize;

    public ManyKeysIntegerToIntegerIndexExtension() { this(-1); }

    public ManyKeysIntegerToIntegerIndexExtension(int size) { cacheSize = size; }

    @Override
    public int getVersion() {
      return VERSION;
    }

    @Override
    public int getCacheSize() {
      if (cacheSize > 0) {
        return cacheSize;
      }
      return super.getCacheSize();
    }

    @Override
    public boolean dependsOnFileContent() {
      return true;
    }

    @Override
    public @NotNull ID<Integer, Integer> getName() {
      return INDEX_ID;
    }

    @Override
    public @NotNull DataExternalizer<Integer> getValueExternalizer() {
      return EnumeratorIntegerDescriptor.INSTANCE;
    }

    @Override
    public @NotNull KeyDescriptor<Integer> getKeyDescriptor() {
      return EnumeratorIntegerDescriptor.INSTANCE;
    }

    @Override
    public @NotNull DataIndexer<Integer, Integer, FileContent> getIndexer() {
      throw new UnsupportedOperationException("Method not implemented: indexation is not tested by this test");
    }

    @Override
    public @NotNull FileBasedIndex.InputFilter getInputFilter() {
      throw new UnsupportedOperationException("Method not implemented: indexation is not tested by this test");
    }

    @Override
    public boolean hasSnapshotMapping() {
      return true;
    }

    @Override
    public boolean needsForwardIndexWhenSharing() {
      return false;
    }

    @Override
    public String toString() {
      return "ManyKeysIntegerToIntegerIndexExtension";
    }
  }

  /** Copied from IdIndex, simplified for testing */
  public static class ShardableManyKeysIntegerToIntegerIndexExtension extends FileBasedIndexExtension<Integer, Integer>
    implements ShardableIndexExtension {
    public static final @NonNls ID<Integer, Integer> INDEX_ID = ID.create("ShardableManyKeysIntegerToIntegerIndexExtension");

    public static final int VERSION = 42;

    private final int cacheSize;

    public ShardableManyKeysIntegerToIntegerIndexExtension() { this(-1); }

    public ShardableManyKeysIntegerToIntegerIndexExtension(int size) { cacheSize = size; }

    @Override
    public int getVersion() {
      return VERSION;
    }

    @Override
    public int shardlessVersion() {
      return VERSION + (shardsCount() - 1) * 10;
    }

    @Override
    public int shardsCount() {
      return 3;
    }

    @Override
    public int getCacheSize() {
      if (cacheSize > 0) {
        return cacheSize;
      }
      return super.getCacheSize();
    }

    @Override
    public boolean dependsOnFileContent() {
      return true;
    }

    @Override
    public @NotNull ID<Integer, Integer> getName() {
      return INDEX_ID;
    }

    @Override
    public @NotNull DataExternalizer<Integer> getValueExternalizer() {
      return EnumeratorIntegerDescriptor.INSTANCE;
    }

    @Override
    public @NotNull KeyDescriptor<Integer> getKeyDescriptor() {
      return EnumeratorIntegerDescriptor.INSTANCE;
    }

    @Override
    public @NotNull DataIndexer<Integer, Integer, FileContent> getIndexer() {
      throw new UnsupportedOperationException("Method not implemented: indexation is not tested by this test");
    }

    @Override
    public @NotNull FileBasedIndex.InputFilter getInputFilter() {
      throw new UnsupportedOperationException("Method not implemented: indexation is not tested by this test");
    }

    @Override
    public boolean hasSnapshotMapping() {
      return true;
    }

    @Override
    public boolean needsForwardIndexWhenSharing() {
      return false;
    }

    @Override
    public String toString() {
      return "ShardableManyKeysIntegerToIntegerIndexExtension";
    }
  }

  /** Generates input data with >=1 key per file */
  public static class ManyEntriesPerFileInputGenerator implements InputDataGenerator<Integer, Integer> {
    public static final int DEFAULT_MAX_FILE_ID = 2_000_000;
    public static final int DEFAULT_KEYS_PER_FILE = 512;
    public static final int DEFAULT_MIN_FILE_ID = PersistentFSRecordsStorage.MIN_VALID_ID;

    private final int minFileId;
    private final int maxFileId;

    private final int maxKeysPerFile;
    private final KeysGenerator keyGenerator;

    /**
     * If null, {@link ThreadLocalRandom} is used (default option).
     * Non-null value is mostly not for alternative Random implementations (standard one is good enough), but to be
     * able to set up {@code Random(fixedSeed)} during debugging
     */
    private final @Nullable Random rnd;

    public ManyEntriesPerFileInputGenerator() {
      this(DEFAULT_MIN_FILE_ID, DEFAULT_MAX_FILE_ID, DEFAULT_KEYS_PER_FILE);
    }

    public ManyEntriesPerFileInputGenerator(int minFileId,
                                            int maxFileId,
                                            int maxKeysPerFile) {
      this(null, minFileId, maxFileId, maxKeysPerFile, new RealisticKeysGenerator());
    }


    public ManyEntriesPerFileInputGenerator(@Nullable Random rnd,
                                            int minFileId,
                                            int maxFileId,
                                            int maxKeysPerFile,
                                            @NotNull KeysGenerator keysGenerator) {
      this.rnd = rnd;
      this.minFileId = minFileId;
      this.maxFileId = maxFileId;
      this.maxKeysPerFile = maxKeysPerFile;
      keyGenerator = keysGenerator;
    }

    @Override
    public @NotNull LongStream generateInputSubstrate() {
      Random rnd = (this.rnd != null) ? this.rnd : ThreadLocalRandom.current();

      return rnd.ints(minFileId, maxFileId)
        .distinct()
        .mapToLong(
          inputId -> {
            int keysCount = rnd.nextInt(maxKeysPerFile) & 0xFFFF;
            return ((long)inputId) << 32
                   | keysCount;
          }
        );
    }

    @Override
    public @NotNull Input<Integer, Integer> unpackSubstrate(long substrate) {
      int inputId = (int)(substrate >> 32);
      int keysCount = (int)(substrate & 0xFFFF);
      Map<Integer, Integer> keyValues = new Int2IntOpenHashMap();
      keyGenerator.keys(/*seed: */ inputId)
        .limit(keysCount)
        .forEach(key -> keyValues.put(key, key));
      return new Input<>(inputId, keyValues);
    }

    @Override
    public String toString() {
      return "ManyEntriesPerFileInputGenerator[" +
             "inputIds:[" + minFileId + ".." + maxFileId + "], " +
             "maxKeysPerFile=" + maxKeysPerFile + ", " +
             "rnd=" + rnd +
             ']';
    }
  }


  public static class SingleEntryIntegerValueIndexExtension extends SingleEntryFileBasedIndexExtension<Integer> {
    private static final ID<Integer, Integer> INDEX_ID = ID.create("SingleEntryIntegerValueIndexExtension");

    @Override
    public int getVersion() {
      return 42;
    }

    @Override
    public @NotNull ID<Integer, Integer> getName() {
      return INDEX_ID;
    }

    @Override
    public @NotNull SingleEntryIndexer<Integer> getIndexer() {
      throw new UnsupportedOperationException("Method not implemented: indexation is not tested by this test");
    }

    @Override
    public @NotNull DataExternalizer<Integer> getValueExternalizer() {
      return EnumeratorIntegerDescriptor.INSTANCE;
    }

    @Override
    public @NotNull FileBasedIndex.InputFilter getInputFilter() {
      throw new UnsupportedOperationException("Method not implemented: indexation is not tested by this test");
    }

    @Override
    public boolean hasSnapshotMapping() {
      return true;
    }

    @Override
    public String toString() {
      return "SingleEntryIntegerValueIndexExtension";
    }
  }

  /** Generates input data with <=1 key per file */
  public static class SingleEntryPerFileInputGenerator implements InputDataGenerator<Integer, Integer> {
    private final int minFileId;
    private final int maxFileId;

    /**
     * If null, {@link ThreadLocalRandom} is used (default option).
     * Non-null value is mostly not for alternative Random implementations (standard one is good enough), but to be
     * able to set up {@code Random(fixedSeed)} during debugging
     */
    private final @Nullable Random rnd;
    private final int differentValuesToGenerate;

    public SingleEntryPerFileInputGenerator() {
      this(null, 1, 10_000_000, 1024);
    }

    public SingleEntryPerFileInputGenerator(@Nullable Random rnd,
                                            int minFileId,
                                            int maxFileId,
                                            int differentValuesToGenerate) {
      this.rnd = rnd;
      this.minFileId = minFileId;
      this.maxFileId = maxFileId;
      this.differentValuesToGenerate = differentValuesToGenerate;
    }

    @Override
    public @NotNull LongStream generateInputSubstrate() {
      Random rnd = (this.rnd != null) ? this.rnd : ThreadLocalRandom.current();

      return rnd.ints(minFileId, maxFileId)
        .distinct()
        .mapToLong(
          inputId -> {
            int value = rnd.nextInt(differentValuesToGenerate);
            return ((long)inputId) << 32
                   | (long)value;
          }
        );
    }

    @Override
    public @NotNull Input<Integer, Integer> unpackSubstrate(long substrate) {
      int inputId = (int)(substrate >> 32);
      int value = (int)(substrate);
      return new Input<>(inputId, Map.of(inputId, value));
    }

    @Override
    public String toString() {
      return "SingleKeyPerFileInputGenerator[" +
             "inputIds=[" + minFileId + ".." + maxFileId + "], " +
             "differentValuesToGenerate=" + differentValuesToGenerate + ", " +
             "rnd=" + rnd +
             ']';
    }
  }

  /**
   * Generates keys with some similarity to real-life keys distribution, to test-cover apt branches in code.
   * Real-life keys distribution for many indexes are heavy-tailed, i.e. there are many rare keys, with only
   * a few inputIds associated with them, and there are very frequent keys, with very long inputIds associated
   * with them. Generator tries to emulate this property (not very precisely, though)
   */
  public static class RealisticKeysGenerator implements KeysGenerator {
    @Override
    public @NotNull IntStream keys(int seed) {
      Random rnd = new Random(seed);
      // Ideally, we should sample from something like Zipf-distribution, which seems to be a good approximation
      // of real-life words/symbols/trigrams distribution, but for now I decided to use simplest piecewise approximation:
      //MAYBE RC: implement sampling from real Zipf keys distribution?

      return rnd.ints(0, 1000)
        .map(random -> {
          if (random < 10) {               //1%,  10 keys,  ~0.1%/key
            return rnd.nextInt(0, 10);
          }
          else if (random < 200) {          //10%, 990 keys, ~0.01%/key
            return rnd.nextInt(10, 1000);
          }
          else {                            //80%, 999_000 keys, ~0.0001%/key
            return rnd.nextInt(1000, 1_000_000);
          }
        })
        .distinct();
    }
  }

  /** Generates keys from U[0..maxKey) -- i.e. all the keys from [0...maxKey) are generated with equal probability */
  public static class UniformKeysGenerator implements KeysGenerator {

    private final int maxKey;

    public UniformKeysGenerator() {
      this(Integer.MAX_VALUE);
    }

    public UniformKeysGenerator(int maxKey) {
      if (maxKey <= 0) {
        throw new IllegalArgumentException("maxKey(=" + maxKey + ") must be >0");
      }
      this.maxKey = maxKey;
    }

    @Override
    public @NotNull IntStream keys(int seed) {
      Random rnd = new Random(seed);
      return rnd.ints(0, maxKey).distinct();
    }
  }

  public static final class Setups {
    private Setups() {
      throw new AssertionError("Not for instantiation, just a namespace");
    }

    /** Provide {@code static Stream<SetupToTest<?, ?>> setupsToTest()} method in a test class, to override and supply own setups */
    private static Stream<SetupToTest<?, ?>> defaultSetupsToTest() {
      return Stream.of(
        new SetupToTest<>(new ManyKeysIntegerToIntegerIndexExtension(), new ManyEntriesPerFileInputGenerator()),
        new SetupToTest<>(new SingleEntryIntegerValueIndexExtension(), new SingleEntryPerFileInputGenerator()),
        new SetupToTest<>(new ShardableManyKeysIntegerToIntegerIndexExtension(), new ManyEntriesPerFileInputGenerator())
      );
    }

    /**
     * <p>Input data and extension is not independent, but intrinsically linked. Simple example: if extension is a
     * {@link SingleEntryFileBasedIndexExtension} then data shouldn't have >1 key per inputId. </p>
     * <p>In a regular scenario this happens naturally, since the extension produces the data to index via
     * {@link FileBasedIndexExtension#getIndexer()}.</p>
     * In the testing scenarios we work around the indexer, and generate faked data instead -- because it is much simpler,
     * and also because index storages should be +/- data-agnostic -- but still, that faked data should be suitable for
     * the extension.
     */
    public record SetupToTest<K, V>(@NotNull FileBasedIndexExtension<K, V> extension,
                                    @NotNull InputDataGenerator<K, V> inputDataGenerator) {
    }

    private static class SetupsToTestProvider implements ArgumentsProvider {
      @Override
      public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
        Class<?> testClass = context.getTestClass().orElseThrow(() -> new AssertionError("Test class must be define"));

        Stream<SetupToTest<?, ?>> setups = defaultSetupsToTest();

        try {
          Method setupsFactoryMethod = ReflectionUtil.getMethod(testClass, "setupsToTest");
          if (setupsFactoryMethod != null && Modifier.isStatic(setupsFactoryMethod.getModifiers())) {
            //noinspection unchecked
            setups = (Stream<SetupToTest<?, ?>>)setupsFactoryMethod.invoke(null);
          }
        }
        catch (Throwable e) {
          //TODO RC: log the error?
        }


        return setups.map(setup -> Arguments.of(setup.extension, setup.inputDataGenerator));
      }
    }
  }

  /** It could be too many errors => risk of very slow test, and OoM */
  static class LimitedErrorsList<T> {
    private final int maxErrorsToKeep;
    private final List<T> errors = new ArrayList<>();
    private int errorsCount = 0;

    LimitedErrorsList(int maxErrorsToKeep) {
      this.maxErrorsToKeep = maxErrorsToKeep;
    }

    public void add(T error) {
      if (errorsCount < maxErrorsToKeep) {
        errors.add(error);
      }
      errorsCount++;
    }

    public boolean isEmpty() {
      return errors.isEmpty();
    }

    public String dump() {
      String title;
      if (errorsCount <= maxErrorsToKeep) {
        title = errorsCount + " errors";
      }
      else {
        title = errorsCount + " errors (first " + maxErrorsToKeep + " shown)";
      }
      return title + "\n\t" + errors.stream().map(Object::toString).collect(joining("\n\t"));
    }
  }
}
