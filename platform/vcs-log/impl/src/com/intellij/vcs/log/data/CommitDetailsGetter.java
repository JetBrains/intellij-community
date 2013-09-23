package com.intellij.vcs.log.data;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.VcsCommitDetails;
import com.intellij.vcs.log.VcsLogProvider;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * The CommitDetailsGetter is responsible for getting {@link VcsCommitDetails complete commit details} from the cache or from the VCS.
 *
 * @author Kirill Likhodedov
 */
public class CommitDetailsGetter extends DataGetter<VcsCommitDetails> {

  CommitDetailsGetter(VcsLogDataHolder dataHolder, @NotNull Map<VirtualFile, VcsLogProvider> logProviders) {
    super(dataHolder, logProviders, new VcsCommitCache<VcsCommitDetails>());
  }

  @NotNull
  @Override
  protected List<? extends VcsCommitDetails> readDetails(@NotNull VcsLogProvider logProvider, @NotNull VirtualFile root,
                                                         @NotNull List<String> hashes) throws VcsException {
    return logProvider.readDetails(root, hashes);
  }

}
