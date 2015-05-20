package com.intellij.vcs.log.data;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsLogHashMap;
import com.intellij.vcs.log.VcsLogProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * The CommitDetailsGetter is responsible for getting {@link VcsFullCommitDetails complete commit details} from the cache or from the VCS.
 */
public class CommitDetailsGetter extends AbstractDataGetter<VcsFullCommitDetails> {

  CommitDetailsGetter(@NotNull VcsLogHashMap hashMap,
                      @NotNull Map<VirtualFile, VcsLogProvider> logProviders,
                      @NotNull Disposable parentDisposable) {
    super(hashMap, logProviders, new VcsCommitCache<Integer, VcsFullCommitDetails>(), parentDisposable);
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
    return logProvider.readFullDetails(root, hashes);
  }

}
