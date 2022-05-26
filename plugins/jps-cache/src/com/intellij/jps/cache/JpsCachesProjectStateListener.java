package com.intellij.jps.cache;

import com.intellij.jps.cache.client.JpsServerAuthUtil;
import com.intellij.jps.cache.loader.JpsOutputLoaderManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.vcs.log.Hash;
import git4idea.GitLocalBranch;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryChangeListener;
import org.jetbrains.annotations.NotNull;

public class JpsCachesProjectStateListener implements GitRepositoryChangeListener {
  private static final Logger LOG = Logger.getInstance(JpsCachesProjectStateListener.class);
  private String previousCommitId = "";

  @Override
  public void repositoryChanged(@NotNull GitRepository repository) {
    GitLocalBranch branch = repository.getCurrentBranch();
    if (branch == null) {
      LOG.warn("Repository is not on a branch");
      return;
    }
    Hash commitHash = repository.getInfo().getRemoteBranchesWithHashes().get(branch.findTrackedBranch(repository));
    if (commitHash == null) return;
    String currentRevision = commitHash.toString();
    if (currentRevision == null || previousCommitId.equals(currentRevision)) return;
    previousCommitId = currentRevision;
    LOG.info("Remote repository commit changed to " + currentRevision);
    JpsOutputLoaderManager outputLoaderManager = JpsOutputLoaderManager.getInstance(repository.getProject());
    JpsServerAuthUtil.checkAuthenticatedInBackgroundThread(outputLoaderManager, repository.getProject(),
                                                           () -> outputLoaderManager.notifyAboutNearestCache());
  }
}
