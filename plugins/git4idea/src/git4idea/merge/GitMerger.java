/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.merge;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitBranch;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.branch.GitBranchUtil;
import git4idea.commands.GitCommand;
import git4idea.commands.GitSimpleHandler;
import git4idea.repo.GitRepositoryFiles;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;

/**
 *
 * @author Kirill Likhodedov
 */
public class GitMerger {

  private final Project myProject;
  private final GitVcs myVcs;

  public GitMerger(Project project) {
    myProject = project;
    myVcs = GitVcs.getInstance(project);
  }


  public Collection<VirtualFile> getMergingRoots() {
    final Collection<VirtualFile> mergingRoots = new HashSet<VirtualFile>();
    for (VirtualFile root : ProjectLevelVcsManager.getInstance(myProject).getRootsUnderVcs(myVcs)) {
      if (GitMergeUtil.isMergeInProgress(root)) {
        mergingRoots.add(root);
      }
    }
    return mergingRoots;
  }

  public void mergeCommit(Collection<VirtualFile> roots) throws VcsException {
    for (VirtualFile root : roots) {
      mergeCommit(root);
    }
  }

  public void mergeCommit(VirtualFile root) throws VcsException {
    GitSimpleHandler handler = new GitSimpleHandler(myProject, root, GitCommand.COMMIT);

    File gitDir = new File(VfsUtilCore.virtualToIoFile(root), GitUtil.DOT_GIT);
    File messageFile = new File(gitDir, GitRepositoryFiles.MERGE_MSG);
    if (!messageFile.exists()) {
      final GitBranch branch = GitBranchUtil.getCurrentBranch(myProject, root);
      final String branchName = branch != null ? branch.getName() : "";
      handler.addParameters("-m", "Merge branch '" + branchName + "' of " + root.getPresentableUrl() + " with conflicts.");
    } else {
      handler.addParameters("-F", messageFile.getAbsolutePath());
    }
    handler.endOptions();
    handler.run();
  }

}
