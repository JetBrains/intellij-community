// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.data;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.vcs.log.VcsCommitMetadata;
import com.intellij.vcs.log.VcsLogObjectsFactory;
import com.intellij.vcs.log.VcsLogProvider;
import com.intellij.vcs.log.data.index.IndexDataGetter;
import com.intellij.vcs.log.data.index.IndexedDetails;
import com.intellij.vcs.log.data.index.VcsLogIndex;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class MiniDetailsGetter extends AbstractDataGetter<VcsCommitMetadata> {

  @NotNull private final TopCommitsCache myTopCommitsDetailsCache;
  @NotNull private final VcsLogObjectsFactory myFactory;

  MiniDetailsGetter(@NotNull Project project,
                    @NotNull VcsLogStorage storage,
                    @NotNull Map<VirtualFile, VcsLogProvider> logProviders,
                    @NotNull TopCommitsCache topCommitsDetailsCache,
                    @NotNull VcsLogIndex index,
                    @NotNull Disposable parentDisposable) {
    super(storage, logProviders, index, parentDisposable);
    myTopCommitsDetailsCache = topCommitsDetailsCache;
    myFactory = project.getService(VcsLogObjectsFactory.class);
  }

  @RequiresBackgroundThread
  public void loadCommitsData(@NotNull Iterable<Integer> hashes,
                              @NotNull Consumer<? super VcsCommitMetadata> consumer,
                              @NotNull ProgressIndicator indicator) throws VcsException {
    final IntSet toLoad = new IntOpenHashSet();

    for (int id : hashes) {
      VcsCommitMetadata details = getFromCache(id);
      if (details == null || details instanceof LoadingDetails) {
        toLoad.add(id);
      }
      else {
        consumer.consume(details);
      }
    }

    if (!toLoad.isEmpty()) {
      indicator.checkCanceled();
      preLoadCommitData(toLoad, consumer);
      notifyLoaded();
    }
  }

  @Nullable
  @Override
  protected VcsCommitMetadata getFromAdditionalCache(int commitId) {
    return myTopCommitsDetailsCache.get(commitId);
  }

  @Override
  protected void readDetails(@NotNull VcsLogProvider logProvider,
                             @NotNull VirtualFile root,
                             @NotNull List<String> hashes,
                             @NotNull Consumer<? super VcsCommitMetadata> consumer) throws VcsException {
    logProvider.readMetadata(root, hashes, consumer);
  }

  @Override
  protected void preLoadCommitData(@NotNull IntSet commits, @NotNull Consumer<? super VcsCommitMetadata> consumer)
    throws VcsException {

    IndexDataGetter dataGetter = getIndex().getDataGetter();
    if (dataGetter == null) {
      super.preLoadCommitData(commits, consumer);
      return;
    }

    IntSet notIndexed = new IntOpenHashSet();

    commits.forEach(commit -> {
      VcsCommitMetadata metadata = IndexedDetails.createMetadata(commit, dataGetter, getStorage(), myFactory);
      if (metadata == null) {
        notIndexed.add(commit);
      }
      else {
        saveInCache(commit, metadata);
        consumer.consume(metadata);
      }
    });

    if (!notIndexed.isEmpty()) {
      super.preLoadCommitData(notIndexed, consumer);
    }
  }
}
