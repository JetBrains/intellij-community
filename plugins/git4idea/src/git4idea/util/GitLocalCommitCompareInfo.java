// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.util;

import com.intellij.dvcs.repo.Repository;
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
import com.intellij.vcsUtil.VcsFileUtil;
import git4idea.GitUtil;
import git4idea.branch.GitBranchWorker;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitLineHandler;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GitLocalCommitCompareInfo extends LocalCommitCompareInfo {
  private final @NotNull Project myProject;
  private final @NotNull String myBranchName;

  public GitLocalCommitCompareInfo(@NotNull Project project,
                                   @NotNull String branchName) {
    myProject = project;
    myBranchName = branchName;
  }

  private void reloadTotalDiff() throws VcsException {
    Map<Repository, Collection<Change>> newDiff = new HashMap<>();
    for (Repository repository: getRepositories()) {
      newDiff.put(repository, GitBranchWorker.loadTotalDiff(repository, myBranchName));
    }

    updateTotalDiff(newDiff);
  }

  @Override
  public void copyChangesFromBranch(@NotNull List<? extends Change> changes,
                                    boolean swapSides) throws VcsException {

    MultiMap<VirtualFile, FilePath> toCheckout = MultiMap.createSet();
    MultiMap<VirtualFile, FilePath> toDelete = MultiMap.createSet();

    for (Change change : changes) {
      FilePath currentPath = swapSides ? ChangesUtil.getAfterPath(change) : ChangesUtil.getBeforePath(change);
      FilePath branchPath = !swapSides ? ChangesUtil.getAfterPath(change) : ChangesUtil.getBeforePath(change);
      assert currentPath != null || branchPath != null;
      VirtualFile root = GitUtil.getRootForFile(myProject, ObjectUtils.chooseNotNull(currentPath, branchPath));
      if (currentPath != null && branchPath != null) {
        if (!Comparing.equal(currentPath, branchPath)) {
          toDelete.putValue(root, currentPath);
        }
        toCheckout.putValue(root, branchPath);
      }
      else if (currentPath != null) {
        toDelete.putValue(root, currentPath);
      }
      else {
        toCheckout.putValue(root, branchPath);
      }
    }

    for (Map.Entry<VirtualFile, Collection<FilePath>> entry : toDelete.entrySet()) {
      VirtualFile root = entry.getKey();
      Collection<FilePath> rootPaths = entry.getValue();

      GitFileUtils.deletePaths(myProject, root, rootPaths);
    }

    for (Map.Entry<VirtualFile, Collection<FilePath>> entry : toCheckout.entrySet()) {
      VirtualFile root = entry.getKey();
      Collection<FilePath> rootPaths = entry.getValue();

      for (List<String> paths : VcsFileUtil.chunkPaths(root, rootPaths)) {
        GitLineHandler handler = new GitLineHandler(myProject, root, GitCommand.CHECKOUT);
        handler.addParameters(myBranchName);
        handler.endOptions();
        handler.addParameters(paths);
        GitCommandResult result = Git.getInstance().runCommand(handler);
        result.throwOnError();
      }

      GitFileUtils.addPaths(myProject, root, rootPaths);
    }

    RefreshVFsSynchronously.updateChanges(changes);
    VcsDirtyScopeManager.getInstance(myProject).filePathsDirty(ChangesUtil.getPaths(changes), null);

    reloadTotalDiff();
  }
}
