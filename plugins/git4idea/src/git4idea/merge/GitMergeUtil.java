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
package git4idea.merge;

import com.intellij.history.Label;
import com.intellij.history.LocalHistory;
import com.intellij.ide.util.ElementsChooser;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.TransactionRunnable;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx;
import com.intellij.openapi.vcs.update.ActionInfo;
import com.intellij.openapi.vcs.update.FileGroup;
import com.intellij.openapi.vcs.update.UpdateInfoTree;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.actions.GitRepositoryAction;
import git4idea.commands.GitCommand;
import git4idea.commands.GitSimpleHandler;
import git4idea.i18n.GitBundle;
import git4idea.util.StringScanner;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.*;

/**
 * Utilities for merge
 */
public class GitMergeUtil {
  /**
   * The item representing default strategy
   */
  public static final String DEFAULT_STRATEGY = GitBundle.getString("merge.default.strategy");

  /**
   * A private constructor for utility class
   */
  private GitMergeUtil() {
  }


  /**
   * Get a list of merge strategies for the specified branch count
   *
   * @param branchCount a number of branches to merge
   * @return an array of strategy names
   */
  @NonNls
  public static String[] getMergeStrategies(int branchCount) {
    if (branchCount < 0) {
      throw new IllegalArgumentException("Branch count must be non-negative: " + branchCount);
    }
    switch (branchCount) {
      case 0:
        return new String[]{DEFAULT_STRATEGY};
      case 1:
        return new String[]{DEFAULT_STRATEGY, "resolve", "recursive", "octopus", "ours", "subtree"};
      default:
        return new String[]{DEFAULT_STRATEGY, "octopus", "ours"};
    }
  }

  /**
   * Setup strategies combobox. The set of strategies changes according to amount of selected elements in branchChooser.
   *
   * @param branchChooser a branch chooser
   * @param strategy      a strategy selector
   */
  public static void setupStrategies(final ElementsChooser<String> branchChooser, final JComboBox strategy) {
    final ElementsChooser.ElementsMarkListener<String> listener = new ElementsChooser.ElementsMarkListener<String>() {
      private void updateStrategies(final List<String> elements) {
        strategy.removeAllItems();
        for (String s : getMergeStrategies(elements.size())) {
          strategy.addItem(s);
        }
        strategy.setSelectedItem(DEFAULT_STRATEGY);
      }

      public void elementMarkChanged(final String element, final boolean isMarked) {
        final List<String> elements = branchChooser.getMarkedElements();
        if (elements.size() == 0) {
          strategy.setEnabled(false);
          updateStrategies(elements);
        }
        else {
          strategy.setEnabled(true);
          updateStrategies(elements);
        }
      }
    };
    listener.elementMarkChanged(null, true);
    branchChooser.addElementsMarkListener(listener);
  }

  /**
   * Show updates caused by git operation
   *
   * @param project     the context project
   * @param exceptions  the exception list
   * @param root        the git root
   * @param currentRev  the revision before update
   * @param beforeLabel the local history label before update
   * @param actionName  the action name
   * @param actionInfo  the information about the action
   */
  public static void showUpdates(GitRepositoryAction action,
                                 final Project project,
                                 final List<VcsException> exceptions,
                                 final VirtualFile root,
                                 final GitRevisionNumber currentRev,
                                 final Label beforeLabel,
                                 final String actionName,
                                 final ActionInfo actionInfo) {
    final UpdatedFiles files = UpdatedFiles.create();
    MergeChangeCollector collector = new MergeChangeCollector(project, root, currentRev);
    collector.collect(files, exceptions);
    if (exceptions.size() != 0) {
      return;
    }
    action.delayTask(new TransactionRunnable() {
      public void run(List<VcsException> exceptionList) {
        ProjectLevelVcsManagerEx manager = (ProjectLevelVcsManagerEx)ProjectLevelVcsManager.getInstance(project);
        UpdateInfoTree tree = manager.showUpdateProjectInfo(files, actionName, actionInfo, false);
        tree.setBefore(beforeLabel);
        tree.setAfter(LocalHistory.getInstance().putSystemLabel(project, "After update"));
      }
    });
    final Collection<String> unmergedNames = files.getGroupById(FileGroup.MERGED_WITH_CONFLICT_ID).getFiles();
    if (!unmergedNames.isEmpty()) {
      action.delayTask(new TransactionRunnable() {
        public void run(List<VcsException> exceptionList) {
          LocalFileSystem lfs = LocalFileSystem.getInstance();
          final ArrayList<VirtualFile> unmerged = new ArrayList<VirtualFile>();
          for (String fileName : unmergedNames) {
            VirtualFile f = lfs.findFileByPath(fileName);
            if (f != null) {
              unmerged.add(f);
            }
          }
          AbstractVcsHelper.getInstance(project).showMergeDialog(unmerged, GitVcs.getInstance(project).getMergeProvider());
        }
      });
    }
  }


  /**
   * @param root the vcs root
   * @return the path to merge head file
   */
  @Nullable
  private static File getMergeHead(@NotNull VirtualFile root) {
    File gitDir = new File(VfsUtil.virtualToIoFile(root), ".git");
    File f = new File(gitDir, "MERGE_HEAD");
    if (f.exists()) {
      return f;
    }
    return null;
  }

  /**
   * @return true if merge is going on for the given git root, false if there is no merge operation in progress.
   */
  @Deprecated
  public static boolean isMergeInProgress(@NotNull VirtualFile root) {
    return getMergeHead(root) != null;
  }

  /**
   * @return unmerged files in the given Git roots, all in a single collection.
   * @see #getUnmergedFiles(com.intellij.openapi.project.Project, com.intellij.openapi.vfs.VirtualFile)
   */
  public static Collection<VirtualFile> getUnmergedFiles(@NotNull Project project, @NotNull Collection<VirtualFile> roots) throws VcsException {
    final Collection<VirtualFile> unmergedFiles = new HashSet<VirtualFile>();
    for (VirtualFile root : roots) {
      unmergedFiles.addAll(getUnmergedFiles(project, root));
    }
    return unmergedFiles;
  }

  /**
   * @return unmerged files in the given Git root.
   * @see #getUnmergedFiles(com.intellij.openapi.project.Project, java.util.Collection)
   */
  public static Collection<VirtualFile> getUnmergedFiles(@NotNull Project project, @NotNull VirtualFile root) throws VcsException {
    return unmergedFiles(project, root);
  }

  /**
   * Parse changes from lines
   *
   * @param project the context project
   * @param root    the git root
   * @return a set of unmerged files
   * @throws com.intellij.openapi.vcs.VcsException if the input format does not matches expected format
   */
  public static List<VirtualFile> unmergedFiles(Project project, VirtualFile root) throws VcsException {
    HashSet<VirtualFile> unmerged = new HashSet<VirtualFile>();
    String rootPath = root.getPath();
    GitSimpleHandler h = new GitSimpleHandler(project, root, GitCommand.LS_FILES);
    h.setNoSSH(true);
    h.setSilent(true);
    h.addParameters("--unmerged");
    LocalFileSystem lfs = LocalFileSystem.getInstance();
    for (StringScanner s = new StringScanner(h.run()); s.hasMoreData();) {
      if (s.isEol()) {
        s.nextLine();
        continue;
      }
      s.boundedToken('\t');
      final String relative = s.line();
      String path = rootPath + "/" + GitUtil.unescapePath(relative);
      VirtualFile file = lfs.refreshAndFindFileByPath(path);
      if (file != null) {
      // the file name is in the delete- or rename- conflict, so it is shown in the list of unmerged files,
      // but the file itself doesn't exist. In that case we just ignore the file.
        file.refresh(false, false);
        unmerged.add(file);
      }
    }
    if (unmerged.size() == 0) {
      return Collections.emptyList();
    }
    else {
      ArrayList<VirtualFile> rc = new ArrayList<VirtualFile>(unmerged.size());
      rc.addAll(unmerged);
      Collections.sort(rc, GitUtil.VIRTUAL_FILE_COMPARATOR);
      return rc;
    }
  }
}
