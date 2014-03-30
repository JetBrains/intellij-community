package com.intellij.vcs.log.data;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsLogProvider;
import com.intellij.vcs.log.VcsShortCommitDetails;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class MiniDetailsGetter extends DataGetter<VcsShortCommitDetails> {

  MiniDetailsGetter(@NotNull VcsLogDataHolder dataHolder, @NotNull Map<VirtualFile, VcsLogProvider> logProviders) {
    super(dataHolder, logProviders, new VcsCommitCache<VcsShortCommitDetails>());
  }

  @Nullable
  @Override
  protected VcsShortCommitDetails getFromAdditionalCache(@NotNull Hash hash) {
    return myDataHolder.getTopCommitDetails(hash);
  }

  @NotNull
  @Override
  protected List<? extends VcsShortCommitDetails> readDetails(@NotNull VcsLogProvider logProvider, @NotNull VirtualFile root,
                                                  @NotNull List<String> hashes) throws VcsException {
    return logProvider.readShortDetails(root, hashes);
  }

}
