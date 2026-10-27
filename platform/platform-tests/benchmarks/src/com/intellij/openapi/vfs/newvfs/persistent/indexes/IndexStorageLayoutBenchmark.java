// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.indexes;

import com.intellij.mock.MockProject;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.util.io.storages.database.DurableMapImplementation;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Processor;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.FileBasedIndexExtension;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.indexing.FileIndexingStateWithExplanation;
import com.intellij.util.indexing.IdFilter;
import com.intellij.util.indexing.IndexedFile;
import com.intellij.util.indexing.IndexStorageLayoutProviderTestBase.Input;
import com.intellij.util.indexing.IndexStorageLayoutProviderTestBase.InputDataGenerator;
import com.intellij.util.indexing.IndexStorageLayoutProviderTestBase.ManyEntriesPerFileInputGenerator;
import com.intellij.util.indexing.IndexStorageLayoutProviderTestBase.ManyKeysIntegerToIntegerIndexExtension;
import com.intellij.util.indexing.IndexStorageLayoutProviderTestBase.RealisticKeysGenerator;
import com.intellij.util.indexing.StorageException;
import com.intellij.util.indexing.VfsAwareIndexStorage;
import com.intellij.util.indexing.impl.IndexDebugProperties;
import com.intellij.util.indexing.impl.IndexStorage;
import com.intellij.util.indexing.impl.InputData;
import com.intellij.util.indexing.impl.forward.AbstractMapForwardIndexAccessor;
import com.intellij.util.indexing.impl.forward.IterableForwardIndex;
import com.intellij.util.indexing.impl.storage.KeyHashLog;
import com.intellij.util.indexing.impl.storage.durablemap.database.DurableDatabaseIndexLayoutProvider;
import com.intellij.util.indexing.storage.FileBasedIndexLayoutProvider;
import com.intellij.util.indexing.storage.MapReduceIndexBase;
import com.intellij.util.indexing.storage.VfsAwareIndexStorageLayout;
import com.intellij.util.indexing.storage.sharding.ShardableIndexExtension;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.CommandLineOptionException;
import org.openjdk.jmh.runner.options.CommandLineOptions;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.IntPredicate;

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

  private static final String DURABLE_DATABASE_PROVIDER_CLASS_NAME =
    "com.intellij.util.indexing.impl.storage.durablemap.database.DurableDatabaseIndexLayoutProvider";


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
    public final InputDataGenerator<Integer, Integer> inputDataGenerator =
      new ManyEntriesPerFileInputGenerator(new Random(42), 1, 5_000_000, 1 << 10, new RealisticKeysGenerator());

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
      DURABLE_DATABASE_PROVIDER_CLASS_NAME + "#KEY_VALUE_TOGETHER",
      DURABLE_DATABASE_PROVIDER_CLASS_NAME + "#KEY_VALUE_SEPARATELY",
      //RC: for the file-based provider to work, add 'index.storages.plugin.mmapped' to the classpath
      "com.intellij.index.storages.plugin.mmapped.DurableMapBasedFileIndexLayoutProvider"
    })
    private String storageLayoutProviderClassName;

    /// Introduced to test WAL effect on DefaultIndexStorageLayoutProvider -- useless for mmapped providers
    @Param({/*"disabled", */"persistent"})
    public String walMode;

    /// Introduced to test WAL effect on DefaultIndexStorageLayoutProvider -- useless for mmapped providers since mmapped
    /// buffer writes bypass anything we could inject from java
    @Param({"none"/*, "write_200us", "write_1ms_force_2ms", "write_200us_p99_50ms"*/})
    public String ioDelayProfile;

    private FileBasedIndexLayoutProvider storageLayoutProviderToTest;

    private IoLatencyProfile latencyProfile;

    //MAYBE configure with @Param?
    public FileBasedIndexExtension<Integer, Integer> extension;


    private VfsAwareIndexStorageLayout<Integer, Integer> storageLayout;

    public IndexStorage<Integer, Integer> indexStorage;

    private BenchmarkMapReduceIndex mapReduceIndex;

    @Setup
    public void setupBenchmark() throws Exception {
      IndexDebugProperties.IS_IN_STRESS_TESTS = true;

      extension = createIndexExtension();

      latencyProfile = IoLatencyProfile.parse(ioDelayProfile);
      int configurationSeparator = storageLayoutProviderClassName.lastIndexOf('#');
      String providerClassName = configurationSeparator < 0
                                 ? storageLayoutProviderClassName
                                 : storageLayoutProviderClassName.substring(0, configurationSeparator);
      String mapImplementationName = configurationSeparator < 0
                                     ? null
                                     : storageLayoutProviderClassName.substring(configurationSeparator + 1);
      if (IndexStorageLayoutBenchmarkStorageContexts.isDefaultProvider(providerClassName)) {
        storageLayoutProviderToTest = IndexStorageLayoutBenchmarkStorageContexts.defaultProvider(
          walMode,
          latencyProfile,
          /*async WAL flusher: */ true
        );
      }
      else if (DURABLE_DATABASE_PROVIDER_CLASS_NAME.equals(providerClassName)) {
        if (mapImplementationName == null) {
          throw new IllegalArgumentException("The durable database provider requires an explicit map implementation");
        }
        storageLayoutProviderToTest = new DurableDatabaseIndexLayoutProvider(
          PathManager.getIndexRoot().resolve("indexing-database-" + mapImplementationName),
          DurableMapImplementation.KEY_VALUE_SEPARATELY,
          DurableMapImplementation.valueOf(mapImplementationName)
        );
      }
      else {
        if (mapImplementationName != null) {
          throw new IllegalArgumentException("A map implementation is not supported for " + providerClassName);
        }
        @SuppressWarnings("unchecked")
        Class<FileBasedIndexLayoutProvider> providerClass = (Class)Class.forName(providerClassName);
        storageLayoutProviderToTest = providerClass.getDeclaredConstructor().newInstance();
      }

      storageLayout = storageLayoutProviderToTest.getLayout(extension, Collections.emptyList());
    }

    @Setup(Level.Iteration)
    public void setupIteration_recreateCleanStorage() throws Exception {
      storageLayout.clearIndexData();
      indexStorage = storageLayout.openIndexStorage();
      resetIoLatencyStatistics();
    }

    @TearDown(Level.Iteration)
    public void closeIteration_closeStorage() throws Exception {
      try {
        closeStorage();
      }
      finally {
        printIoLatencyStatistics();
      }
    }

    @TearDown
    public void closeBenchmark() throws Exception {
      closeStorage();
      if (storageLayout != null) {
        storageLayout.clearIndexData();
        storageLayout = null;
      }

      if (storageLayoutProviderToTest != null) {
        storageLayoutProviderToTest.close();
      }
    }

    private void closeStorage() throws IOException {
      if (mapReduceIndex != null) {
        mapReduceIndex.dispose();
        mapReduceIndex = null;
        indexStorage = null;
      }
      else if (indexStorage != null) {
        indexStorage.close();
        indexStorage = null;
      }
    }

    public void resetIoLatencyStatistics() {
      if (latencyProfile != null) {
        latencyProfile.resetStatistics();
      }
    }

    private void printIoLatencyStatistics() {
      if (latencyProfile != null) {
        String statistics = latencyProfile.drainStatistics();
        if (!statistics.isEmpty()) {
          System.out.println(statistics);
        }
      }
    }

    protected FileBasedIndexExtension<Integer, Integer> createIndexExtension() {
      return new SampleIndexExtension(cacheSize);
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
  public static class ProjectFilterStorageContext extends StorageContext {
    @Override
    protected FileBasedIndexExtension<Integer, Integer> createIndexExtension() {
      return new SampleIndexExtension(cacheSize) {
        @Override
        public @NotNull DataIndexer<Integer, Integer, FileContent> getIndexer() {
          return ignoredContent -> {
            throw new UnsupportedOperationException("The benchmark supplies mapped data directly");
          };
        }

        @Override
        public boolean traceKeyHashToVirtualFileMapping() {
          return true;
        }
      };
    }
  }

  /// Exposes the base key traversal without VFS indexing services
  private static final class BenchmarkMapReduceIndex extends MapReduceIndexBase<Integer, Integer, Void> {
    private BenchmarkMapReduceIndex(FileBasedIndexExtension<Integer, Integer> extension,
                                    IndexStorage<Integer, Integer> storage,
                                    VfsAwareIndexStorageLayout<Integer, Integer> layout) throws IOException {
      super(extension, () -> storage, layout::openForwardIndex, layout.getForwardIndexAccessor());
    }

    @Override
    protected Logger getLogger() {
      return Logger.getInstance(BenchmarkMapReduceIndex.class);
    }

    @Override
    protected void requestRebuild(@NotNull Throwable e) {
      throw new IllegalStateException("The benchmark index requires a rebuild", e);
    }

    @Override
    public Void getFileIndexMetaData(@NotNull IndexedFile file) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void invalidateIndexedStateForFile(int fileId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setUnindexedStateForFile(int fileId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public @NotNull FileIndexingStateWithExplanation getIndexingStateForFile(int fileId, @NotNull IndexedFile file) {
      throw new UnsupportedOperationException();
    }
  }

  @State(Scope.Benchmark)
  public static class ReadContext {

    private int[] allKeys;

    @Setup(Level.Iteration)
    public void setupIteration_fillStorageWithData(InputContext inputContext,
                                                   StorageContext storageContext) throws Exception {
      fillStorageWithData(inputContext, storageContext, /* keyHashLog: */ null);
    }

    private void fillStorageWithData(InputContext inputContext,
                                     StorageContext storageContext,
                                     @Nullable KeyHashLog<Integer> keyHashLog) throws Exception {
      InputDataGenerator<Integer, Integer> dataGenerator = inputContext.inputDataGenerator;
      IndexStorage<Integer, Integer> indexStorage = storageContext.indexStorage;
      IntOpenHashSet keys = new IntOpenHashSet();
      for (long substrate : inputContext.inputsSubstrate) {
        Input<Integer, Integer> input = dataGenerator.unpackSubstrate(substrate);
        int inputId = input.inputId();
        for (Map.Entry<Integer, Integer> e : input.inputData().entrySet()) {
          indexStorage.addValue(e.getKey(), inputId, e.getValue());
          if (keyHashLog != null) {
            keyHashLog.addKeyHashToVirtualFileMapping(e.getKey(), inputId);
          }
          keys.add(e.getKey().intValue());
        }
      }
      indexStorage.flush();

      allKeys = keys.toIntArray();
      storageContext.resetIoLatencyStatistics();
    }

    public Integer nextKey() {
      //TODO RC: we should sample keys from the same distribution we have generated them from -- i.e. more frequent keys
      //         should be looked up more frequently than the rare keys. Otherwise this is
      int keyIndex = ThreadLocalRandom.current().nextInt(allKeys.length);
      return allKeys[keyIndex];
    }
  }

  public enum KeyFilterMode { KEYS_ONLY, EXACT, HASH_LOG, FORWARD_INDEX }

  /// Keeps the project filter ready so repeated queries measure the scan rather than cache construction
  @SuppressWarnings("unused") // JMH calls the setup and teardown methods
  @State(Scope.Benchmark)
  public static class ProjectFilterContext {
    @Param({"KEYS_ONLY", "EXACT", "HASH_LOG", "FORWARD_INDEX"})
    public KeyFilterMode keyFilterMode;

    @Param("1/4")
    public String acceptedFileIdFraction;

    private int acceptedFileIdNumerator;
    private int acceptedFileIdDenominator;

    private final IdFilter idFilter = new IdFilter() {
      @Override
      public boolean containsFileId(int id) {
        return id > 0 && id % acceptedFileIdDenominator < acceptedFileIdNumerator;
      }

      @Override
      public @NotNull FilterScopeType getFilteringScopeType() {
        return FilterScopeType.PROJECT_AND_LIBRARIES;
      }
    };

    private Disposable disposable;
    private MockProject project;
    private GlobalSearchScope scope;
    private Path logDirectory;
    private KeyHashLog<Integer> keyHashLog;

    @Setup
    public void setupBenchmark() {
      String[] fraction = acceptedFileIdFraction.split("/", -1);
      if (fraction.length != 2) {
        throw new IllegalArgumentException("The file ID fraction must have the form numerator/denominator");
      }
      acceptedFileIdNumerator = Integer.parseInt(fraction[0]);
      acceptedFileIdDenominator = Integer.parseInt(fraction[1]);
      if (acceptedFileIdDenominator <= 0 || acceptedFileIdNumerator < 0 || acceptedFileIdNumerator > acceptedFileIdDenominator) {
        throw new IllegalArgumentException("The file ID fraction must be between 0 and 1 with a positive denominator");
      }
      if (!KeyHashLog.ENABLE_CACHED_HASH_IDS) {
        throw new IllegalStateException("The benchmark requires idea.index.cashed.hashids=true");
      }
      disposable = Disposer.newDisposable();
      project = new MockProject(null, disposable);
      scope = new GlobalSearchScope(project) {
        @Override
        public boolean isSearchInModuleContent(@NotNull Module module) {
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
    }

    @Setup(Level.Iteration)
    public void setupIteration(InputContext inputContext, ProjectFilterStorageContext storageContext) throws Exception {
      StorageContext context = storageContext;
      context.mapReduceIndex = new BenchmarkMapReduceIndex(context.extension, context.indexStorage, context.storageLayout);
      if (!(context.mapReduceIndex.getForwardIndex() instanceof IterableForwardIndex forwardIndex) ||
          !(context.mapReduceIndex.getForwardIndexAccessor() instanceof AbstractMapForwardIndexAccessor<Integer, Integer, ?> accessor)) {
        throw new IllegalStateException("The benchmark requires an iterable forward index and a map accessor");
      }
      logDirectory = Files.createTempDirectory("index-key-hash-log");
      keyHashLog = new KeyHashLog<>(storageContext.extension.getKeyDescriptor(), logDirectory.resolve("keys"));
      var readContext = new ReadContext();
      readContext.fillStorageWithData(inputContext, storageContext, keyHashLog);
      for (long substrate : inputContext.inputsSubstrate) {
        var input = inputContext.inputDataGenerator.unpackSubstrate(substrate);
        forwardIndex.put(input.inputId(), accessor.serializeIndexedData(new InputData<>(input.inputData())));
      }
      context.mapReduceIndex.flush();

      // Build the cache and check the filters before measuring repeated queries
      IntPredicate suitableHashes = keyHashLog.getSuitableKeyHashes(idFilter, project);
      int hashFilteredCount = 0;
      for (int key : readContext.allKeys) {
        if (suitableHashes.test(storageContext.extension.getKeyDescriptor().getHashCode(key))) hashFilteredCount++;
      }
      int[] exactCount = {0};
      var exactKeys = new IntOpenHashSet();
      var storage = (VfsAwareIndexStorage<Integer, Integer>)storageContext.indexStorage;
      storage.processKeys(key -> {
        exactCount[0]++;
        exactKeys.add(key.intValue());
        return true;
      }, scope, idFilter);
      if (exactKeys.size() != hashFilteredCount) {
        throw new IllegalStateException("The exact filter and the hash filter disagree: " + exactKeys.size() + " != " + hashFilteredCount);
      }
      var forwardKeys = new IntOpenHashSet();
      context.mapReduceIndex.processAllKeys(key -> {
        if (!forwardKeys.add(key.intValue())) {
          throw new IllegalStateException("The forward index returned a duplicate key: " + key);
        }
        return true;
      }, scope, idFilter);
      if (!forwardKeys.equals(exactKeys)) {
        throw new IllegalStateException("The forward index and the exact filter returned different keys");
      }
      System.out.println("Project key filter: all=" + readContext.allKeys.length + ", matched=" + exactKeys.size() +
                         ", exactProcessorCalls=" + exactCount[0] + ", forwardProcessorCalls=" + forwardKeys.size());
      storageContext.resetIoLatencyStatistics();
    }

    @TearDown(Level.Iteration)
    public void closeIteration() throws Exception {
      try {
        if (keyHashLog != null) keyHashLog.close();
      }
      finally {
        if (logDirectory != null) NioFiles.deleteRecursively(logDirectory);
      }
    }

    @TearDown
    public void closeBenchmark() {
      if (disposable != null) Disposer.dispose(disposable);
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

  @Benchmark
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  public int indexStorage_forEachKeyWithProjectFilter(ProjectFilterStorageContext storageContext,
                                                      ProjectFilterContext filterContext) throws StorageException {
    var storage = (VfsAwareIndexStorage<Integer, Integer>)storageContext.indexStorage;
    int[] count = {0};
    Processor<Integer> processor = ignored -> {
      count[0]++;
      return true;
    };
    switch (filterContext.keyFilterMode) {
      case KEYS_ONLY -> storage.processKeys(processor, filterContext.scope, IdFilter.ACCEPT_ALL);
      case EXACT -> storage.processKeys(processor, filterContext.scope, filterContext.idFilter);
      case FORWARD_INDEX -> {
        StorageContext context = storageContext;
        context.mapReduceIndex.processAllKeys(processor, filterContext.scope, filterContext.idFilter);
      }
      case HASH_LOG -> {
        IntPredicate suitableHashes = filterContext.keyHashLog.getSuitableKeyHashes(filterContext.idFilter, filterContext.project);
        storage.processKeys(key -> {
          if (!suitableHashes.test(storageContext.extension.getKeyDescriptor().getHashCode(key))) return true;
          return processor.process(key);
        }, filterContext.scope, IdFilter.ACCEPT_ALL);
      }
    }
    return count[0];
  }


  static void main(String[] args) throws RunnerException, CommandLineOptionException {
    var optionsBuilder = new OptionsBuilder();
    optionsBuilder.jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED",
                           "--add-opens=java.base/java.util=ALL-UNNAMED",
                           "--add-opens=java.desktop/java.awt=ALL-UNNAMED",
                           "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
                           "--add-opens=java.desktop/sun.font=ALL-UNNAMED",
                           "--add-opens=java.desktop/java.awt.event=ALL-UNNAMED",
                           "--add-exports=java.base/sun.nio.ch=ALL-UNNAMED",
                           "--add-exports=java.base/jdk.internal.ref=ALL-UNNAMED",

                           //disable cache:
                           //"-Didea.use.slru.for.file.based.index=false",

                           "-Xmx4g");
    if (args.length == 0) {
      //.mode(Mode.SingleShotTime)
      //.warmupIterations(1000)
      //.warmupBatchSize(1000)
      //.measurementIterations(1000)
      //.include(IndexStorageLayoutBenchmark.class.getSimpleName() + ".*addValues.*")
      optionsBuilder.include(IndexStorageLayoutBenchmark.class.getSimpleName() + ".*");
      //.threads(4)
      //.mode(Mode.SampleTime)
      //.forks(1)
    }
    else {
      optionsBuilder.parent(new CommandLineOptions(args));
    }

    new Runner(optionsBuilder.build()).run();
  }
}
