// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.indexes;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.indexing.FileBasedIndexExtension;
import com.intellij.util.indexing.IdFilter;
import com.intellij.util.indexing.IndexStorageLayoutProviderTestBase.Input;
import com.intellij.util.indexing.IndexStorageLayoutProviderTestBase.InputDataGenerator;
import com.intellij.util.indexing.IndexStorageLayoutProviderTestBase.ManyEntriesPerFileInputGenerator;
import com.intellij.util.indexing.IndexStorageLayoutProviderTestBase.ManyKeysIntegerToIntegerIndexExtension;
import com.intellij.util.indexing.StorageException;
import com.intellij.util.indexing.VfsAwareIndexStorage;
import com.intellij.util.indexing.impl.IndexDebugProperties;
import com.intellij.util.indexing.impl.IndexStorage;
import com.intellij.util.indexing.storage.FileBasedIndexLayoutProvider;
import com.intellij.util.indexing.storage.VfsAwareIndexStorageLayout;
import com.intellij.util.indexing.storage.sharding.ShardableIndexExtension;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.jetbrains.annotations.NotNull;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Benchmark for different {@link com.intellij.util.indexing.impl.IndexStorageLayout} implementations
 */
@BenchmarkMode({Mode.AverageTime/*, Mode.SampleTime*/})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 5, timeUnit = SECONDS)
@Measurement(iterations = 2, time = 15, timeUnit = SECONDS)
@Fork(1)
public class IndexStorageLayoutBenchmark {


  @State(Scope.Benchmark)
  public static class InputContext {

    @Param("200000")
    public int inputsCountToTestWith;

    //RC: benchmark performance is actually quite sensitive to the keys distribution -- the more 'spread' the distribution,
    //    i.e. the more keys in total, the more uneven the inputIds distributed among the keys -- the worse performance is.
    //    The caching efficacy is likely the cause of this: the larger count(keys)/cacheSize is => the less effective the
    //    caching is => the more exposed the performance of underlying storages is.
    //    Which is why, probably, Durable implementation is less affected by the keys distribution: its underlying storage
    //    performance is generally better.
    //MAYBE RC: we should test index storages with & without caching, to get more robust description about actual storage
    //          performance. No-cache numbers is be harder to relate to high-level index subsystem performance, though -- but
    //          we could have both kinds of tests, with and without caching, to see how the caching smoothens the underlying
    //          storage performance.
    public final InputDataGenerator<Integer, Integer> inputDataGenerator = new ManyEntriesPerFileInputGenerator(1, 5_000_000, 1 << 10);

    public long[] inputsSubstrate;

    @Setup
    public void setupBenchmark() throws Exception {
      IndexDebugProperties.IS_IN_STRESS_TESTS = true;

      inputsSubstrate = inputDataGenerator.generateInputSubstrate()
        .limit(inputsCountToTestWith)
        .toArray();
    }

    @Setup(Level.Iteration)
    public void setupIteration_resetIndex() throws Exception {
      index = 0;
    }

    private int index = 0;

    private Input<Integer, Integer> nextInput() {
      index = (index + 1) % inputsSubstrate.length;
      long substrate = inputsSubstrate[index];

      return inputDataGenerator.unpackSubstrate(substrate);
    }
  }

  @State(Scope.Benchmark)
  public static class StorageContext {

    //1024 is a default value for multi-key indexes
    @Param({"1024", "16384"})
    public int cacheSize;

    @Param({
      "com.intellij.util.indexing.impl.storage.DefaultIndexStorageLayoutProvider",
      //RC: for this to work add 'index.storages.plugin.mmapped' to the classpath
      "com.intellij.index.storages.plugin.mmapped.DurableMapBasedFileIndexLayoutProvider"
    })
    private String storageLayoutProviderClassName;

    private FileBasedIndexLayoutProvider storageLayoutProviderToTest;

    //MAYBE configure with @Param?
    public FileBasedIndexExtension<Integer, Integer> extension;


    private VfsAwareIndexStorageLayout<Integer, Integer> storageLayout;

    public IndexStorage<Integer, Integer> indexStorage;

    @Setup
    public void setupBenchmark() throws Exception {
      IndexDebugProperties.IS_IN_STRESS_TESTS = true;

      extension = new SampleIndexExtension(cacheSize);

      @SuppressWarnings("unchecked")
      Class<FileBasedIndexLayoutProvider> providerClass = (Class)Class.forName(storageLayoutProviderClassName);
      storageLayoutProviderToTest = providerClass.getDeclaredConstructor().newInstance();

      storageLayout = storageLayoutProviderToTest.getLayout(extension, Collections.emptyList());
    }

    @Setup(Level.Iteration)
    public void setupIteration_recreateCleanStorage() throws Exception {
      storageLayout.clearIndexData();
      indexStorage = storageLayout.openIndexStorage();
    }

    @TearDown(Level.Iteration)
    public void closeIteration_closeStorage() throws Exception {
      if (indexStorage != null) {
        indexStorage.close();
      }
    }

    @TearDown
    public void closeBenchmark() throws Exception {
      if (indexStorage != null) {
        indexStorage.close();
      }
      if (storageLayout != null) {
        storageLayout.clearIndexData();
      }
    }

    public static class SampleIndexExtension extends ManyKeysIntegerToIntegerIndexExtension implements ShardableIndexExtension {

      public SampleIndexExtension(int cacheSize) {
        super(cacheSize);
      }

      @Override
      public int shardsCount() {
        return 2;
      }

      @Override
      public int shardlessVersion() {
        return super.getVersion();
      }
    }
  }

  @State(Scope.Benchmark)
  public static class ReadContext {

    private int[] allKeys;

    @Setup(Level.Iteration)
    public void setupIteration_fillStorageWithData(InputContext inputContext,
                                                   StorageContext storageContext) throws Exception {
      InputDataGenerator<Integer, Integer> dataGenerator = inputContext.inputDataGenerator;
      IndexStorage<Integer, Integer> indexStorage = storageContext.indexStorage;
      IntOpenHashSet keys = new IntOpenHashSet();
      for (long substrate : inputContext.inputsSubstrate) {
        Input<Integer, Integer> input = dataGenerator.unpackSubstrate(substrate);
        int inputId = input.inputId();
        for (Map.Entry<Integer, Integer> e : input.inputData().entrySet()) {
          indexStorage.addValue(e.getKey(), inputId, e.getValue());
          keys.add(e.getKey().intValue());
        }
      }
      indexStorage.flush();

      allKeys = keys.toIntArray();
    }

    public Integer nextKey() {
      //TODO RC: we should sample keys from the same distribution we have generated them from -- i.e. more frequent keys
      //         should be looked up more frequently than the rare keys. Otherwise this is
      int keyIndex = ThreadLocalRandom.current().nextInt(allKeys.length);
      return allKeys[keyIndex];
    }
  }


  @Benchmark
  public int baseline_nextInputGeneration(InputContext inputContext) throws Exception {
    Input<Integer, Integer> input = inputContext.nextInput();
    return input.inputId();
  }


  @Benchmark
  public int indexStorage_addValues(InputContext inputContext,
                                    StorageContext storageContext) throws Exception {
    IndexStorage<Integer, Integer> indexStorage = storageContext.indexStorage;
    Input<Integer, Integer> input = inputContext.nextInput();
    int inputId = input.inputId();
    for (Map.Entry<Integer, Integer> e : input.inputData().entrySet()) {
      indexStorage.addValue(e.getKey(), inputId, e.getValue());
    }
    return inputId;
  }

  @Benchmark
  public boolean indexStorage_readContainer(InputContext inputContext,
                                        StorageContext storageContext,
                                        ReadContext readContext) throws StorageException {
    IndexStorage<Integer, Integer> indexStorage = storageContext.indexStorage;

    Integer key = readContext.nextKey();
    return indexStorage.read(key, container -> {
      container.size();//.size() forces load/merge data:
      return true;
    });
  }

  private static final GlobalSearchScope SCOPE_EVERYTHING = new GlobalSearchScope() {
    @Override
    public boolean isSearchInModuleContent(@NotNull Module aModule) {
      return true;
    }

    @Override
    public boolean isSearchInLibraries() {
      return true;
    }

    @Override
    public boolean contains(@NotNull VirtualFile file) {
      return true;
    }
  };

  @Benchmark
  public boolean indexStorage_forEachKey(StorageContext storageContext,
                                         ReadContext readContext) throws StorageException {
    VfsAwareIndexStorage<Integer, Integer> storage = (VfsAwareIndexStorage<Integer, Integer>)storageContext.indexStorage;
    return storage.processKeys(
      key -> {
        return true;
      },
      SCOPE_EVERYTHING,
      IdFilter.ACCEPT_ALL
    );
  }


  public static void main(final String[] args) throws RunnerException {
    final Options opt = new OptionsBuilder()
      .jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED",
               "--add-opens=java.base/java.util=ALL-UNNAMED",
               "--add-opens=java.desktop/java.awt=ALL-UNNAMED",
               "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
               "--add-opens=java.desktop/sun.font=ALL-UNNAMED",
               "--add-opens=java.desktop/java.awt.event=ALL-UNNAMED",

               //disable cache:
               //"-Didea.use.slru.for.file.based.index=false",

               "-Xmx4g"
      )
      //.mode(Mode.SingleShotTime)
      //.warmupIterations(1000)
      //.warmupBatchSize(1000)
      //.measurementIterations(1000)
      //.include(IndexStorageLayoutBenchmark.class.getSimpleName() + ".*addValuesToIndexStorage.*")
      .include(IndexStorageLayoutBenchmark.class.getSimpleName() + ".*")
      .threads(1)
      .forks(1)
      .build();

    new Runner(opt).run();
  }
}
