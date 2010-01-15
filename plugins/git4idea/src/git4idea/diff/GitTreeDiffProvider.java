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

package git4idea.diff;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.TreeDiffProvider;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitBranchesSearcher;
import git4idea.changes.GitChangeUtils;
import git4idea.commands.GitCommand;
import git4idea.commands.GitSimpleHandler;
import git4idea.commands.StringScanner;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

public class GitTreeDiffProvider implements TreeDiffProvider {
  private final static Logger LOG = Logger.getInstance("#git4idea.diff.GitTreeDiffProvider");
  private final Project myProject;

  public GitTreeDiffProvider(final Project project) {
    myProject = project;
  }

  public Collection<String> getRemotelyChanged(final VirtualFile vcsRoot, final Collection<String> paths) {
    try {
      final GitBranchesSearcher searcher = new GitBranchesSearcher(myProject, vcsRoot, true);
      if (searcher.getLocal() == null || searcher.getRemote() == null) return Collections.emptyList();

      GitSimpleHandler handler = new GitSimpleHandler(myProject, vcsRoot, GitCommand.DIFF);
      handler.addParameters("--name-status", "--diff-filter=ADCMRUX", "-M", "HEAD..." + searcher.getRemote().getFullName());
      handler.setNoSSH(true);
      handler.setSilent(true);
      handler.setStdoutSuppressed(true);
      handler.endOptions();
      final Collection<File> files = new ArrayList<File>(paths.size());
      for (String path : paths) {
        files.add(new File(path));
      }
      handler.addRelativePathsForFiles(files);

      String output = handler.run();
      return GitChangeUtils.parseDiffForPaths(vcsRoot.getPath(), new StringScanner(output));
    }
    catch (VcsException e) {
      LOG.info(e);
      return Collections.emptyList();
    }
  }
}
