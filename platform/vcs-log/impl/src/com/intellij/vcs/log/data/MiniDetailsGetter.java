package com.intellij.vcs.log.data;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.VcsLogProvider;
import com.intellij.util.containers.ConcurrentIntObjectMap;
import com.intellij.vcs.log.VcsCommitMetadata;
import com.intellij.vcs.log.VcsLogStorage;
import com.intellij.vcs.log.VcsShortCommitDetails;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class MiniDetailsGetter extends AbstractDataGetter<VcsShortCommitDetails> {

  @NotNull private final TopCommitsCache myTopCommitsDetailsCache;

  MiniDetailsGetter(@NotNull VcsLogStorage hashMap,
                    @NotNull Map<VirtualFile, VcsLogProvider> logProviders,
                    @NotNull TopCommitsCache topCommitsDetailsCache,
                    @NotNull Disposable parentDisposable) {
    super(hashMap, logProviders, new VcsCommitCache<>(), parentDisposable);
    myTopCommitsDetailsCache = topCommitsDetailsCache;
  }

  @Nullable
  @Override
  protected VcsShortCommitDetails getFromAdditionalCache(int commitId) {
    return myTopCommitsDetailsCache.get(commitId);
  }

  @NotNull
  @Override
  protected List<? extends VcsShortCommitDetails> readDetails(@NotNull VcsLogProvider logProvider, @NotNull VirtualFile root,
                                                              @NotNull List<String> hashes) throws VcsException {
    return logProvider.readShortDetails(root, hashes);
  }
}
