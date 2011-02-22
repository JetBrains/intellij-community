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
import com.intellij.openapi.ui.Messages;
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
import com.intellij.util.ui.UIUtil;
import git4idea.GitRevisionNumber;
import git4idea.GitVcs;
import git4idea.actions.GitRepositoryAction;
import git4idea.changes.GitChangeUtils;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

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
   * Initialize no commit checkbox (for both merge and pull dialog)
   *
   * @param addLogInformationCheckBox a log information checkbox
   * @param commitMessage             a commit message text field or null
   * @param noCommitCheckBox          a no commit checkbox to configure
   */
  public static void setupNoCommitCheckbox(final JCheckBox addLogInformationCheckBox,
                                           final JTextField commitMessage,
                                           final JCheckBox noCommitCheckBox) {
    noCommitCheckBox.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        final boolean selected = noCommitCheckBox.isSelected();
        if (commitMessage != null) {
          commitMessage.setEnabled(!selected);
        }
        if (selected) {
          addLogInformationCheckBox.setSelected(false);
        }
        addLogInformationCheckBox.setEnabled(!selected);
      }
    });
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
        UpdateInfoTree tree = manager.showUpdateProjectInfo(files, actionName, actionInfo);
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
    return GitChangeUtils.unmergedFiles(project, root);
  }

  public static MergeResult mergeFiles(final Project project, final VirtualFile root, final boolean reverse) throws VcsException {
    final GitVcs vcs = GitVcs.getInstance(project);
    final AbstractVcsHelper vcsHelper = AbstractVcsHelper.getInstance(project);
    if (vcs == null) {
      return MergeResult.CANCEL_UPDATE;
    }

    final AtomicReference<MergeResult> mergeDecision = new AtomicReference<MergeResult>(MergeResult.ALL_MERGED);
    final AtomicReference<VcsException> ex = new AtomicReference<VcsException>();
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      public void run() {
        try {
          List<VirtualFile> unmergedFiles = GitChangeUtils.unmergedFiles(project, root);
          while (unmergedFiles.size() != 0) {
            vcsHelper.showMergeDialog(unmergedFiles, reverse ? vcs.getReverseMergeProvider() : vcs.getMergeProvider());
            unmergedFiles = GitChangeUtils.unmergedFiles(project, root);
            if (unmergedFiles.size() != 0) {
              int decision = Messages.showDialog(project,
                                                 "There are unresolved merges. Would you like to continue merging, merge later by hands or cancel the update",
                                                 "Unresolved merges", new String[]{"Cancel update", "Merge later", "Merge now"}, 2,
                                                 Messages.getErrorIcon());
              if (decision == 0) {
                mergeDecision.set(MergeResult.CANCEL_UPDATE);
                return;
              } else if (decision == 1) {
                mergeDecision.set(MergeResult.MERGE_LATER);
                return;
              }
            }
          }
        } catch (VcsException t) {
          ex.set(t);
        }
      }
    });
    if (ex.get() != null) {
      throw ex.get();
    }
    return mergeDecision.get();
  }

  public enum MergeResult {
    ALL_MERGED,
    MERGE_LATER,
    CANCEL_UPDATE
  }
}
