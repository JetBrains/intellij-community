/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package git4idea;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.branch.GitBranchUtil;

import java.util.HashSet;
import java.util.Set;

public class GitBranchesSearcher {
  private final static Logger LOG = Logger.getInstance("#git4idea.GitBranchesSearcher");
  private final GitBranch myLocal;
  private GitBranch myRemote;

  public GitBranchesSearcher(final Project project, final VirtualFile root, final boolean findRemote) throws VcsException {
    LOG.debug("constructing, root: " + root.getPath() + " findRemote = " + findRemote);
    final Set<GitBranch> usedBranches = new HashSet<>();
    myLocal = GitBranchUtil.getCurrentBranch(project, root);
    LOG.debug("local: " + myLocal);
    if (myLocal == null) return;
    usedBranches.add(myLocal);

    GitBranch remote = myLocal;
    while (true) {
      remote = GitBranchUtil.tracked(project, root, remote.getName());
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
