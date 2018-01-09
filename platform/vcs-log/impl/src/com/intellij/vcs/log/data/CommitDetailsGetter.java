package com.intellij.vcs.log.data;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsLogProvider;
import com.intellij.vcs.log.data.index.VcsLogIndex;
import com.intellij.vcs.log.util.VcsLogUtil;
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
    super(storage, logProviders, new VcsCommitCache<>(), index, parentDisposable);
  }

  @Nullable
  @Override
  protected VcsFullCommitDetails getFromAdditionalCache(int commitId) {
    return null;
  }

  @NotNull
  @Override
  protected List<? extends VcsFullCommitDetails> readDetails(@NotNull VcsLogProvider logProvider, @NotNull VirtualFile root,
                                                             @NotNull List<String> hashes) throws VcsException {
    return VcsLogUtil.getDetails(logProvider, root, hashes);
  }
}
