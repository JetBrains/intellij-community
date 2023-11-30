// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package git4idea;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.repo.GitBranchTrackInfo;
import git4idea.repo.GitRepository;

import java.util.HashSet;
import java.util.Set;

public class GitBranchesSearcher {
  private static final Logger LOG = Logger.getInstance(GitBranchesSearcher.class);
  private final GitBranch myLocal;
  private GitBranch myRemote;

  public GitBranchesSearcher(final Project project, final VirtualFile root, final boolean findRemote) throws VcsException {
    LOG.debug("constructing, root: " + root.getPath() + " findRemote = " + findRemote);
    final Set<GitBranch> usedBranches = new HashSet<>();
    GitRepository repository = GitUtil.getRepositoryForRoot(project, root);
    myLocal = repository.getCurrentBranch();
    LOG.debug("local: " + myLocal);
    if (myLocal == null) return;
    usedBranches.add(myLocal);

    GitBranch remote = myLocal;
    while (true) {
      GitBranchTrackInfo trackInfo = repository.getBranchTrackInfo(remote.getName());
      remote = trackInfo == null ? null : trackInfo.getRemoteBranch();
      if (remote == null) {
        LOG.debug("remote == null, exiting");
        return;
      }

      if ((! findRemote) || remote.isRemote()) {
        LOG.debug("remote found, isRemote: " + remote.isRemote() + " remoteName: " + remote.getFullName());
        myRemote = remote;
        return;
      }

      if (usedBranches.contains(remote)) {
        LOG.debug("loop found for: " + remote.getFullName() + ", exiting");
        return;
      }
      usedBranches.add(remote);
    }
  }

  public GitBranch getLocal() {
    return myLocal;
  }

  public GitBranch getRemote() {
    return myRemote;
  }
}
