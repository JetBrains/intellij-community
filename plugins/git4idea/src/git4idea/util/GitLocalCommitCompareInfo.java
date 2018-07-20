// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.util;

import com.intellij.dvcs.repo.Repository;
import com.intellij.dvcs.repo.RepositoryManager;
import com.intellij.dvcs.util.LocalCommitCompareInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vcs.update.RefreshVFsSynchronously;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.hash.HashMap;
import com.intellij.vcsUtil.VcsFileUtil;
import git4idea.branch.GitBranchWorker;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitLineHandler;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class GitLocalCommitCompareInfo extends LocalCommitCompareInfo {

  private void reloadTotalDiff(@NotNull String branchName) throws VcsException {
    Map<Repository, Collection<Change>> newDiff = new HashMap<>();
    for (Repository repository: getRepositories()) {
      newDiff.put(repository, GitBranchWorker.loadTotalDiff(repository, branchName));
    }

    updateTotalDiff(newDiff);
  }

  @Override
  public void reloadContentFromBranch(@NotNull Project project,
                                      @NotNull String branchName,
                                      @NotNull List<Change> changes,
                                      boolean swapSides,
                                      @NotNull RepositoryManager repositoryManager) throws VcsException {

    MultiMap<Repository, FilePath> toCheckout = MultiMap.createSet();
    MultiMap<Repository, FilePath> toDelete = MultiMap.createSet();

    for (Change change : changes) {
      FilePath currentPath = swapSides ? ChangesUtil.getAfterPath(change) : ChangesUtil.getBeforePath(change);
      FilePath branchPath = !swapSides ? ChangesUtil.getAfterPath(change) : ChangesUtil.getBeforePath(change);
      assert currentPath != null || branchPath != null;

      Repository repository = repositoryManager.getRepositoryForFile(ObjectUtils.chooseNotNull(currentPath, branchPath));
      if (currentPath != null && branchPath != null) {
        if (Comparing.equal(currentPath, branchPath)) {
          toCheckout.putValue(repository, branchPath);
        }
        else {
          toDelete.putValue(repository, currentPath);
          toCheckout.putValue(repository, branchPath);
        }
      }
      else if (currentPath != null) {
        toDelete.putValue(repository, currentPath);
      }
      else {
        toCheckout.putValue(repository, branchPath);
      }
    }

      for (Map.Entry<Repository, Collection<FilePath>> entry : toDelete.entrySet()) {
        Repository repository = entry.getKey();
        Collection<FilePath> rootPaths = entry.getValue();
        VirtualFile root = repository.getRoot();

        GitFileUtils.delete(project, root, rootPaths);
      }

      for (Map.Entry<Repository, Collection<FilePath>> entry : toCheckout.entrySet()) {
        Repository repository = entry.getKey();
        Collection<FilePath> rootPaths = entry.getValue();
        VirtualFile root = repository.getRoot();

        for (List<String> paths : VcsFileUtil.chunkPaths(root, rootPaths)) {
          GitLineHandler handler = new GitLineHandler(project, root, GitCommand.CHECKOUT);
          handler.addParameters(branchName);
          handler.endOptions();
          handler.addParameters(paths);
          GitCommandResult result = Git.getInstance().runCommand(handler);
          result.getOutputOrThrow();
        }

        GitFileUtils.addPaths(project, root, rootPaths);
      }

      RefreshVFsSynchronously.updateChanges(changes);
      VcsDirtyScopeManager.getInstance(project).filePathsDirty(ChangesUtil.getPaths(changes), null);

      reloadTotalDiff(branchName);
  }
}
