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
package git4idea.update;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManagerEx;
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager;
import com.intellij.openapi.vcs.changes.shelf.ShelvedBinaryFile;
import com.intellij.openapi.vcs.changes.shelf.ShelvedChange;
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.commands.GitCommand;
import git4idea.commands.GitFileUtils;
import git4idea.commands.GitSimpleHandler;
import git4idea.config.GitVersion;
import git4idea.vfs.GitVFSListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.ChangeEvent;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * The class contains utilities for creating and removing stashes.
 */
public class GitStashUtils {
  /**
   * The version when quiet stash supported
   */
  private final static GitVersion QUIET_STASH_SUPPORTED = new GitVersion(1, 6, 4, 0);

  private GitStashUtils() {
  }

  /**
   * Create stash for later use
   *
   * @param project the project to use
   * @param root    the root
   * @param message the message for the stash
   * @return true if the stash was created, false otherwise
   */
  public static boolean saveStash(@NotNull Project project, @NotNull VirtualFile root, final String message) throws VcsException {
    GitSimpleHandler handler = new GitSimpleHandler(project, root, GitCommand.STASH);
    handler.setNoSSH(true);
    handler.addParameters("save", message);
    String output = handler.run();
    return !output.startsWith("No local changes to save");
  }

  /**
   * Create stash for later use (it ignores exit code 1 [merge conflict])
   *
   * @param project the project to use
   * @param root    the root
   */
  public static void popLastStash(@NotNull Project project, @NotNull VirtualFile root) throws VcsException {
    GitSimpleHandler handler = new GitSimpleHandler(project, root, GitCommand.STASH);
    handler.setNoSSH(true);
    handler.addParameters("pop");
    handler.ignoreErrorCode(1);
    if (QUIET_STASH_SUPPORTED.isLessOrEqual(GitVcs.getInstance(project).version())) {
      handler.addParameters("--quiet");
    }
    handler.run();
  }

  /**
   * Perform system level unshelve operation
   *
   * @param project           the project
   * @param shelvedChangeList the shelved change list
   * @param shelveManager     the shelve manager
   * @param changeManager     the change manager
   * @param exceptions        the collected exceptions
   */
  public static void doSystemUnshelve(final Project project,
                                      final ShelvedChangeList shelvedChangeList,
                                      final ShelveChangesManager shelveManager,
                                      final ChangeListManagerEx changeManager,
                                      List<VcsException> exceptions) {
    // The changes are temporary copied to the first local change list, the next operation will restore them back
    VirtualFile baseDir = project.getBaseDir();
    assert baseDir != null;
    String projectPath = baseDir.getPath() + "/";
    // Refresh files that might be affected by unshelve
    HashSet<File> filesToRefresh = new HashSet<File>();
    for (ShelvedChange c : shelvedChangeList.getChanges()) {
      if (c.getBeforePath() != null) {
        filesToRefresh.add(new File(projectPath + c.getBeforePath()));
      }
      if (c.getAfterPath() != null) {
        filesToRefresh.add(new File(projectPath + c.getAfterPath()));
      }
    }
    for (ShelvedBinaryFile f : shelvedChangeList.getBinaryFiles()) {
      if (f.BEFORE_PATH != null) {
        filesToRefresh.add(new File(projectPath + f.BEFORE_PATH));
      }
      if (f.AFTER_PATH != null) {
        filesToRefresh.add(new File(projectPath + f.BEFORE_PATH));
      }
    }
    LocalFileSystem.getInstance().refreshIoFiles(filesToRefresh);
    // Do unshevle
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      public void run() {
        GitVFSListener l = GitVcs.getInstance(project).getVFSListener();
        l.setEventsSuppressed(true);
        try {
          shelveManager
            .unshelveChangeList(shelvedChangeList, shelvedChangeList.getChanges(), shelvedChangeList.getBinaryFiles(),
                                changeManager.getDefaultChangeList(), false);
        }
        finally {
          l.setEventsSuppressed(false);
        }
      }
    });
    Collection<FilePath> paths = new ArrayList<FilePath>();
    for (ShelvedChange c : shelvedChangeList.getChanges()) {
      if (c.getBeforePath() == null || !c.getBeforePath().equals(c.getAfterPath()) || c.getFileStatus() == FileStatus.ADDED) {
        paths.add(VcsUtil.getFilePath(projectPath + c.getAfterPath()));
      }
    }
    for (ShelvedBinaryFile f : shelvedChangeList.getBinaryFiles()) {
      if (f.BEFORE_PATH == null || !f.BEFORE_PATH.equals(f.AFTER_PATH) || f.getFileStatus() == FileStatus.ADDED) {
        paths.add(VcsUtil.getFilePath(projectPath + f.AFTER_PATH));
      }
    }
    Map<VirtualFile, List<FilePath>> map = GitUtil.sortGitFilePathsByGitRoot(paths);
    for (Map.Entry<VirtualFile, List<FilePath>> e : map.entrySet()) {
      try {
        GitFileUtils.addPaths(project, e.getKey(), e.getValue());
      }
      catch (VcsException e1) {
        exceptions.add(e1);
      }
    }
  }

  /**
   * Shelve changes
   *
   * @param project       the context project
   * @param shelveManager the shelve manager
   * @param changes       the changes to process
   * @param description   the description of for the shelve
   * @param exceptions    the generated exceptions
   * @return created shelved change list or null in case failure
   */
  @Nullable
  public static ShelvedChangeList shelveChanges(final Project project, final ShelveChangesManager shelveManager, Collection<Change> changes,
                                                final String description,
                                                final List<VcsException> exceptions) {
    try {
      ShelvedChangeList shelve = shelveManager.shelveChanges(changes, description);
      project.getMessageBus().syncPublisher(ShelveChangesManager.SHELF_TOPIC).stateChanged(new ChangeEvent(GitStashUtils.class));
      return shelve;
    }
    catch (IOException e) {
      //noinspection ThrowableInstanceNeverThrown
      exceptions.add(new VcsException("Shelving changes failed: " + description, e));
      return null;
    }
    catch (VcsException e) {
      exceptions.add(e);
      return null;
    }
  }
}
