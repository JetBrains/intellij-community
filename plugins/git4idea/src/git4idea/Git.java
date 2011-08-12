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
package git4idea;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsFileUtil;
import git4idea.commands.GitCommand;
import git4idea.commands.GitHandlerUtil;
import git4idea.commands.GitLineHandler;
import git4idea.commands.GitSimpleHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Low level layer of Git commands.
 *
 * @author Kirill Likhodedov
 */
public class Git {

  private static final Logger LOG = Logger.getInstance(Git.class);

  private Git() {
  }

  /**
   * Calls 'git init' on the specified directory.
   */
  public static void init(Project project, VirtualFile root) throws VcsException {
    GitLineHandler h = new GitLineHandler(project, root, GitCommand.INIT);
    h.setNoSSH(true);
    GitHandlerUtil.runInCurrentThread(h, null);
    if (!h.errors().isEmpty()) {
      throw h.errors().get(0);
    }
  }

  /**
   * <p>Queries Git for the unversioned files in the given paths.</p>
   * <p>
   *   <b>Note:</b> this method doesn't check for ignored files. You have to check if the file is ignored afterwards, if needed.
   * </p>
   *
   * @param files files that are to be checked for the unversioned files among them.
   *              <b>Pass <code>null</code> to query the whole repository.</b>
   * @return Unversioned files from the given scope.
   */
  @NotNull
  public static Set<VirtualFile> untrackedFiles(@NotNull Project project,
                                                @NotNull VirtualFile root,
                                                @Nullable Collection<VirtualFile> files) throws VcsException {
    final Set<VirtualFile> untrackedFiles = new HashSet<VirtualFile>();

    if (files == null) {
      untrackedFiles.addAll(untrackedFilesNoChunk(project, root, null));
    } else {
      for (List<String> relativePaths : VcsFileUtil.chunkFiles(root, files)) {
        untrackedFiles.addAll(untrackedFilesNoChunk(project, root, relativePaths));
      }
    }

    return untrackedFiles;
  }

  // relativePaths are guaranteed to fit into command line length limitations.
  @NotNull
  private static Collection<VirtualFile> untrackedFilesNoChunk(@NotNull Project project,
                                                               @NotNull VirtualFile root,
                                                               @Nullable List<String> relativePaths)
    throws VcsException {
    final Set<VirtualFile> untrackedFiles = new HashSet<VirtualFile>();
    GitSimpleHandler h = new GitSimpleHandler(project, root, GitCommand.LS_FILES);
    h.setNoSSH(true);
    h.setSilent(true);
    h.addParameters("--exclude-standard", "--others", "-z");
    h.endOptions();
    if (relativePaths != null) {
      h.addParameters(relativePaths);
    }

    final String output = h.run();
    if (StringUtil.isEmptyOrSpaces(output)) {
      return untrackedFiles;
    }

    for (String relPath : output.split("\u0000")) {
      VirtualFile f = root.findFileByRelativePath(relPath);
      if (f == null) {
        // files was created on disk, but VirtualFile hasn't yet been created,
        // when the GitChangeProvider has already been requested about changes.
        LOG.info(String.format("VirtualFile for path [%s] is null", relPath));
      } else {
        untrackedFiles.add(f);
      }
    }

    return untrackedFiles;
  }
}
