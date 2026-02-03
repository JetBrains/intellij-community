// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.testFramework.DynamicPluginTestUtilsKt;
import com.intellij.testFramework.IndexingTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileBasedIndexExtension;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.indexing.ID;
import com.intellij.util.indexing.SingleEntryIndexer;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorIntegerDescriptor;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

public class IndexingTest extends JavaCodeInsightFixtureTestCase {

  public void testInfiniteIndexingWithUnstableEncoding() {
    List<Class<? extends SimplePropertyFileIndexer>> indexers =
      List.of(SimpleFirstPropertyFileIndexer.class, SimpleSecondPropertyFileIndexer.class, SimpleThirdPropertyFileIndexer.class);
    indexers.forEach(IndexingTest::resetCount);

    List<Disposable> disposables = ContainerUtil.map(indexers, indexer -> DynamicPluginTestUtilsKt.loadExtensionWithText(
      "<fileBasedIndex implementation=\"" + indexer.getName() + "\"/>",
      "com.intellij"
    ));
    disposables.forEach(d -> Disposer.register(getTestRootDisposable(), d));
    indexers.forEach(IndexingTest::resetCount);

    for (Class<? extends SimplePropertyFileIndexer> indexer : indexers) {
      assertEquals(indexer.getName() + " should not be called", 0, getCount(indexer));
    }

    int filesCount = 20; // see: ChangedFilesCollector.MIN_CHANGES_TO_PROCESS_ASYNC
    WriteAction.compute(() -> {
      return IntStream.range(0, filesCount).mapToObj(i -> {
        try {
          VirtualFile file = myFixture.createFile((i == 0 ? "test" : ("test_" + i)) + ".properties", "");
          //noinspection NonAsciiCharacters
          file.setBinaryContent("""
                                  one=eins
                                  two=zwei
                                  three=drei
                                  four=vier
                                  five=fünf
                                  six=sechs
                                  seven=sieben
                                  eight=acht
                                  nine=neun
                                  ten=zehn
                                  """.getBytes(StandardCharsets.ISO_8859_1));
          return file;
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }).toList();
    });

    waitForIndexing();

    for (Class<? extends SimplePropertyFileIndexer> indexer : indexers) {
      assertNotSame(indexer.getName() + ": the property file should be indexed", 0, getCount(indexer));
      // maximum 3 times
      assertTrue(indexer.getName() + ": the property has been indexed too many times: " + getCount(indexer), getCount(indexer) < 4);
    }
  }

  private void waitForIndexing() {
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    IndexingTestUtil.waitUntilIndexesAreReady(getProject());
  }

  private static abstract class SimplePropertyFileIndexer extends FileBasedIndexExtension<Integer, String> {
    private static final Object LOCK = new Object();
    protected static final String OBSERVED_FILE = "test.properties";

    @Override
    public @NotNull SingleEntryIndexer<String> getIndexer() {
      return new SingleEntryIndexer<>(true) {
        @Override
        protected String computeValue(@NotNull FileContent inputData) {
          synchronized (LOCK) {
            return compute(inputData);
          }
        }
      };
    }

    abstract String compute(@NotNull FileContent inputData);

    @Override
    public @NotNull DataExternalizer<String> getValueExternalizer() {
      return EnumeratorStringDescriptor.INSTANCE;
    }

    @Override
    public int getVersion() {
      return 1;
    }

    @Override
    public FileBasedIndex.@NotNull InputFilter getInputFilter() {
      return file -> file.getName().endsWith(".properties");
    }

    @Override
    public @NotNull KeyDescriptor<Integer> getKeyDescriptor() {
      return EnumeratorIntegerDescriptor.INSTANCE;
    }

    @Override
    public boolean dependsOnFileContent() {
      return true;
    }
  }

  public static class SimpleFirstPropertyFileIndexer extends SimplePropertyFileIndexer {
    public static final AtomicInteger counter = new AtomicInteger(0);
    private final ID<Integer, String> id = ID.create("test.encoding.text.indexer.first");

    @Override
    String compute(@NotNull FileContent inputData) {
      if (inputData.getFile().getName().equals(OBSERVED_FILE)) counter.incrementAndGet();
      return LoadTextUtil.loadText(inputData.getFile(), 8).toString();
    }

    @Override
    public @NotNull ID<Integer, String> getName() {
      return id;
    }
  }

  public static class SimpleSecondPropertyFileIndexer extends SimplePropertyFileIndexer {
    public static final AtomicInteger counter = new AtomicInteger(0);
    private final ID<Integer, String> id = ID.create("test.encoding.text.indexer.second");

    @Override
    String compute(@NotNull FileContent inputData) {
      if (inputData.getFile().getName().equals(OBSERVED_FILE)) counter.incrementAndGet();
      return LoadTextUtil.loadText(inputData.getFile()).toString();
    }

    @Override
    public @NotNull ID<Integer, String> getName() {
      return id;
    }
  }

  public static class SimpleThirdPropertyFileIndexer extends SimplePropertyFileIndexer {
    public static final AtomicInteger counter = new AtomicInteger(0);
    private final ID<Integer, String> id = ID.create("test.encoding.text.indexer.third");

    @Override
    String compute(@NotNull FileContent inputData) {
      if (inputData.getFile().getName().equals(OBSERVED_FILE)) counter.incrementAndGet();
      return inputData.getFile().getCharset().toString();
    }

    @Override
    public @NotNull ID<Integer, String> getName() {
      return id;
    }
  }

  private static int getCount(Class<? extends SimplePropertyFileIndexer> clazz) {
    if (clazz == SimpleFirstPropertyFileIndexer.class) return SimpleFirstPropertyFileIndexer.counter.get();
    if (clazz == SimpleSecondPropertyFileIndexer.class) return SimpleSecondPropertyFileIndexer.counter.get();
    if (clazz == SimpleThirdPropertyFileIndexer.class) return SimpleThirdPropertyFileIndexer.counter.get();
    throw new IllegalArgumentException(clazz.getName());
  }

  private static void resetCount(Class<? extends SimplePropertyFileIndexer> clazz) {
    if (clazz == SimpleFirstPropertyFileIndexer.class) SimpleFirstPropertyFileIndexer.counter.set(0);
    if (clazz == SimpleSecondPropertyFileIndexer.class) SimpleSecondPropertyFileIndexer.counter.set(0);
    if (clazz == SimpleThirdPropertyFileIndexer.class) SimpleThirdPropertyFileIndexer.counter.set(0);
  }
}
