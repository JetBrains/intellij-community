/*
 * Copyright 2000-2008 JetBrains s.r.o.
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
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.update.FileGroup;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
import git4idea.commands.GitSimpleHandler;

import java.util.HashSet;
import java.util.List;

/**
 * Collect change for merge or pull operations
 */
public class MergeChangeCollector {
  /**
   * Unmerged paths
   */
  private final HashSet<String> myUnmergedPaths = new HashSet<String>();
  /**
   * The context project
   */
  private final Project myProject;
  /**
   * The git root
   */
  private final VirtualFile myRoot;
  /**
   * Updates container
   */
  private final UpdatedFiles myUpdates;
  /**
   * Revision number before update (used for diff)
   */
  private final GitRevisionNumber myStart;

  /**
   * A constructor
   *
   * @param project the context project
   * @param root    the git root
   * @param start   the start revision
   * @param updates a container for updates
   */
  public MergeChangeCollector(final Project project, final VirtualFile root, final GitRevisionNumber start, final UpdatedFiles updates) {
    myStart = start;
    myProject = project;
    myRoot = root;
    myUpdates = updates;
  }

  /**
   * Collect changes
   *
   * @param exceptions a list of exceptions
   */
  public void collect(List<VcsException> exceptions) {
    try {
      // collect unmerged
      String root = myRoot.getPath();
      GitSimpleHandler h = new GitSimpleHandler(myProject, myRoot, "ls-files");
      h.setNoSSH(true);
      h.addParameters("--unmerged");
      for (String line : h.run().split("\n")) {
        if (line.length() == 0) {
          continue;
        }
        String[] tk = line.split("[\t ]+");
        final String relative = tk[tk.length - 1];
        if (!myUnmergedPaths.add(relative)) {
          continue;
        }
        String path = root + "/" + GitUtil.unescapePath(relative);
        myUpdates.getGroupById(FileGroup.MERGED_WITH_CONFLICT_ID).add(path);
      }
      // collect other changes (ignoring unmerged)
      h = new GitSimpleHandler(myProject, myRoot, "diff");
      h.setNoSSH(true);
      // note that moves are not detected here
      h.addParameters("--name-status", "--diff-filter=ADMRUX", myStart.getRev());
      for (String line : h.run().split("\n")) {
        if (line.length() == 0) {
          continue;
        }
        String[] tk = line.split("[\t ]+");
        final String relative = tk[tk.length - 1];
        if (myUnmergedPaths.contains(relative)) {
          continue;
        }
        String path = root + "/" + GitUtil.unescapePath(relative);
        switch (tk[0].charAt(0)) {
          case 'M':
            myUpdates.getGroupById(FileGroup.UPDATED_ID).add(path);
            break;
          case 'A':
            myUpdates.getGroupById(FileGroup.CREATED_ID).add(path);
            break;
          case 'D':
            myUpdates.getGroupById(FileGroup.REMOVED_FROM_REPOSITORY_ID).add(path);
            break;
          default:
            throw new IllegalStateException("Unexpected status: " + line);
        }
      }
    }
    catch (VcsException e) {
      exceptions.add(e);
    }
  }
}
