// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.data;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.LowMemoryWatcher;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsLogProvider;
import com.intellij.vcs.log.data.index.VcsLogIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * The CommitDetailsGetter is responsible for getting {@link VcsFullCommitDetails complete commit details} from the cache or from the VCS.
 */
public class CommitDetailsGetter extends AbstractDataGetter<VcsFullCommitDetails> {

  CommitDetailsGetter(@NotNull VcsLogStorage storage,
                      @NotNull Map<VirtualFile, VcsLogProvider> logProviders,
                      @NotNull VcsLogIndex index,
                      @NotNull Disposable parentDisposable) {
    super(storage, logProviders, index, parentDisposable);
    LowMemoryWatcher.register(() -> clear(), this);
  }

  @Nullable
  @Override
  protected VcsFullCommitDetails getFromAdditionalCache(int commitId) {
    return null;
  }

  @Override
  protected void readDetails(@NotNull VcsLogProvider logProvider,
                             @NotNull VirtualFile root,
                             @NotNull List<String> hashes,
                             @NotNull Consumer<? super VcsFullCommitDetails> consumer) throws VcsException {
    logProvider.readFullDetails(root, hashes, consumer);
  }
}
