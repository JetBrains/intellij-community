package com.intellij.vcs.log.data;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsLogProvider;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * The CommitDetailsGetter is responsible for getting {@link VcsFullCommitDetails complete commit details} from the cache or from the VCS.
 *
 * @author Kirill Likhodedov
 */
public class CommitDetailsGetter extends DataGetter<VcsFullCommitDetails> {

  CommitDetailsGetter(VcsLogDataHolder dataHolder, @NotNull Map<VirtualFile, VcsLogProvider> logProviders) {
    super(dataHolder, logProviders, new VcsCommitCache<VcsFullCommitDetails>());
  }

  @NotNull
  @Override
  protected List<? extends VcsFullCommitDetails> readDetails(@NotNull VcsLogProvider logProvider, @NotNull VirtualFile root,
                                                         @NotNull List<String> hashes) throws VcsException {
    return logProvider.readFullDetails(root, hashes);
  }

}
