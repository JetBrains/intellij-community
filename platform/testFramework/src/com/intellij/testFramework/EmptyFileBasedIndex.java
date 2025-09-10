// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Processor;
import com.intellij.util.indexing.*;
import com.intellij.util.indexing.impl.InputData;
import com.intellij.util.indexing.impl.InputDataDiffBuilder;
import com.intellij.util.indexing.impl.UpdateData;
import com.intellij.util.indexing.impl.ValueContainerProcessor;
import com.intellij.util.io.MeasurableIndexStore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.IntPredicate;

public final class EmptyFileBasedIndex extends FileBasedIndexEx {
  private static final Logger LOG = Logger.getInstance(EmptyFileBasedIndex.class);

  @Override
  public void registerProjectFileSets(@NotNull Project project) {
  }

  @Override
  public void iterateIndexableFiles(@NotNull ContentIterator processor, @NotNull Project project, @Nullable ProgressIndicator indicator) {
  }

  @Override
  public boolean iterateNonIndexableFiles(@NotNull Project project,
                                          @Nullable VirtualFileFilter acceptFilter,
                                          @NotNull ContentIterator processor) {
    return true;
  }

  @Override
  public @Nullable VirtualFile findFileById(int id) {
    return ManagingFS.getInstance().findFileById(id);
  }

  @Override
  public @NotNull Logger getLogger() {
    return LOG;
  }

  @Override
  public @Nullable VirtualFile getFileBeingCurrentlyIndexed() {
    return null;
  }

  @Override
  public @Nullable IndexWritingFile getFileWritingCurrentlyIndexes() {
    return null;
  }

  @Override
  public VirtualFile findFileById(Project project, int id) {
    return null;
  }

  @Override
  public @NotNull <K, V> List<V> getValues(@NotNull ID<K, V> indexId, @NotNull K dataKey, @NotNull GlobalSearchScope filter) {
    return Collections.emptyList();
  }

  @Override
  public @NotNull <K, V> Collection<VirtualFile> getContainingFiles(@NotNull ID<K, V> indexId,
                                                                    @NotNull K dataKey,
                                                                    @NotNull GlobalSearchScope filter) {
    return Collections.emptyList();
  }

  @Override
  public <K, V> boolean processValues(@NotNull ID<K, V> indexId,
                                      @NotNull K dataKey,
                                      @Nullable VirtualFile inFile,
                                      @NotNull ValueProcessor<? super V> processor,
                                      @NotNull GlobalSearchScope filter) {
    return true;
  }

  @Override
  public <K, V> long getIndexModificationStamp(@NotNull ID<K, V> indexId, @NotNull Project project) {
    return 0;
  }

  @Override
  public <K, V> boolean processFilesContainingAllKeys(@NotNull ID<K, V> indexId,
                                                      @NotNull Collection<? extends K> dataKeys,
                                                      @NotNull GlobalSearchScope filter,
                                                      @Nullable Condition<? super V> valueChecker,
                                                      @NotNull Processor<? super VirtualFile> processor) {
    return true;
  }

  @Override
  public <K, V> boolean processFilesContainingAnyKey(@NotNull ID<K, V> indexId,
                                                     @NotNull Collection<? extends K> dataKeys,
                                                     @NotNull GlobalSearchScope filter,
                                                     @Nullable IdFilter idFilter,
                                                     @Nullable Condition<? super V> valueChecker,
                                                     @NotNull Processor<? super VirtualFile> processor) {
    return true;
  }

  @Override
  public @NotNull <K> Collection<K> getAllKeys(@NotNull ID<K, ?> indexId, @NotNull Project project) {
    return Collections.emptyList();
  }

  @Override
  public <K> void ensureUpToDate(@NotNull ID<K, ?> indexId, @Nullable Project project, @Nullable GlobalSearchScope filter) {

  }

  @Override
  public void requestRebuild(@NotNull ID<?, ?> indexId, @NotNull Throwable throwable) {

  }

  @Override
  public <K> void scheduleRebuild(@NotNull ID<K, ?> indexId, @NotNull Throwable e) {

  }

  @Override
  public void requestReindex(@NotNull VirtualFile file) {

  }

  @Override
  public <K, V> boolean getFilesWithKey(@NotNull ID<K, V> indexId,
                                        @NotNull Set<? extends K> dataKeys,
                                        @NotNull Processor<? super VirtualFile> processor,
                                        @NotNull GlobalSearchScope filter) {
    return true;
  }

  @Override
  public <K> boolean processAllKeys(@NotNull ID<K, ?> indexId, @NotNull Processor<? super K> processor, @Nullable Project project) {
    return true;
  }

  @Override
  public @NotNull <K, V> Map<K, V> getFileData(@NotNull ID<K, V> id, @NotNull VirtualFile virtualFile, @NotNull Project project) {
    return Collections.emptyMap();
  }

  @Override
  public void invalidateCaches() {
  }

  @Override
  public @NotNull IntPredicate getAccessibleFileIdFilter(@Nullable Project project) {
    return value -> false;
  }

  @Override
  public @Nullable IdFilter extractIdFilter(@Nullable GlobalSearchScope scope,
                                            @Nullable Project project) {
    return null;
  }

  @Override
  public IdFilter projectIndexableFiles(@Nullable Project project) {
    return null;
  }

  @Override
  public void waitUntilIndicesAreInitialized() {
  }

  @Override
  public <K> boolean ensureUpToDate(@NotNull ID<K, ?> indexId,
                                    @Nullable Project project,
                                    @Nullable GlobalSearchScope filter,
                                    @Nullable VirtualFile restrictedFile) {
    return true;
  }

  @Override
  public @NotNull <K, V> UpdatableIndex<K, V, FileContent, ?> getIndex(ID<K, V> indexId) {
    return EmptyIndex.getInstance();
  }

  private static final class EmptyIndex<Key, Value> implements UpdatableIndex<Key, Value, FileContent, Void>, MeasurableIndexStore {
    @SuppressWarnings("rawtypes")
    private static final EmptyIndex INSTANCE = new EmptyIndex();

    @SuppressWarnings("unchecked")
    static <Key, Value> EmptyIndex<Key, Value> getInstance() {
      return INSTANCE;
    }

    @Override
    public boolean processAllKeys(@NotNull Processor<? super Key> processor,
                                  @NotNull GlobalSearchScope scope,
                                  @Nullable IdFilter idFilter) {
      return true;
    }

    @Override
    public @NotNull Map<Key, Value> getIndexedFileData(int fileId) {
      return Collections.emptyMap();
    }

    @Override
    public Void getFileIndexMetaData(@NotNull IndexedFile file) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setIndexedStateForFileOnFileIndexMetaData(int fileId, @Nullable Void unused, boolean isProvidedByInfrastructureExtension) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setIndexedStateForFile(int fileId, @NotNull IndexedFile file, boolean isProvidedByInfrastructureExtension) {
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

    @Override
    public long getModificationStamp() {
      return 0;
    }

    @Override
    public void removeTransientDataForFile(int inputId) {
    }

    @Override
    public void removeTransientDataForKeys(int inputId, @NotNull InputDataDiffBuilder<Key, Value> diffBuilder) {

    }

    @Override
    public @NotNull IndexExtension<Key, Value, FileContent> getExtension() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void updateWith(@NotNull UpdateData<Key, Value> updateData) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setBufferingEnabled(boolean enabled) {
    }

    @Override
    public void cleanupMemoryStorage() {
    }

    @Override
    public void cleanupForNextTest() {
    }

    @Override
    public boolean isDirty() {
      return false;
    }

    @Override
    public <E extends Exception> boolean withData(@NotNull Key key,
                                                  @NotNull ValueContainerProcessor<Value, E> processor) throws E {
      return processor.process(ValueContainer.emptyContainer());
    }

    @Override
    public @NotNull StorageUpdate mapInputAndPrepareUpdate(int inputId, @Nullable FileContent content) {
      throw new UnsupportedOperationException();
    }

    @Override
    public @NotNull StorageUpdate prepareUpdate(int inputId, @NotNull InputData<Key, Value> data) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void flush() {
    }

    @Override
    public void clear() {
    }

    @Override
    public void dispose() {
    }

    @Override
    public int keysCountApproximately() {
      return 0;
    }
  }
}
