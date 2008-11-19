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
package git4idea.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitBranch;
import git4idea.GitRemote;
import git4idea.GitVcs;
import git4idea.config.GitConfigUtil;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collections;
import java.util.List;

/**
 * Utilities for git plugin user interface
 */
public class GitUIUtil {
  /**
   * Text conaining in the label when there is no current branch
   */
  public static final String NO_CURRENT_BRANCH = GitBundle.getString("common.no.active.branch");

  /**
   * A private constructor for utility class
   */
  private GitUIUtil() {
  }

  /**
   * @return a list cell renderer for virtual files (it renders prestanble URL
   */
  public static ListCellRenderer getVirtualFileListCellRenderer() {
    return new DefaultListCellRenderer() {
      public Component getListCellRendererComponent(final JList list,
                                                    final Object value,
                                                    final int index,
                                                    final boolean isSelected,
                                                    final boolean cellHasFocus) {
        String text = ((VirtualFile)value).getPresentableUrl();
        return super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus);
      }
    };

  }

  /**
   * Create list cell renderd for remotes. It shows both name and url and highlights the default
   * remote for the branch with bold.
   *
   * @param defaultRemote a default remote
   * @return a list cell renderer for virtual files (it renders prestanble URL
   */
  public static ListCellRenderer getGitRemoteListCellRenderer(final String defaultRemote) {
    return new DefaultListCellRenderer() {
      @SuppressWarnings({"HardCodedStringLiteral"})
      public Component getListCellRendererComponent(final JList list,
                                                    final Object value,
                                                    final int index,
                                                    final boolean isSelected,
                                                    final boolean cellHasFocus) {
        final GitRemote remote = (GitRemote)value;
        String startName;
        String endName;
        if (defaultRemote != null && defaultRemote.equals(remote.name())) {
          startName = "<b>";
          endName = "</b>";
        }
        else {
          startName = "";
          endName = "";
        }
        String text = "<html>" + startName + remote.name() + endName + " (<i>" + remote.url() + "</i>)</html>";
        return super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus);
      }
    };

  }


  /**
   * Setup root chooser with specfied elements and link selecto to the current branch label.
   *
   * @param project            a context project
   * @param roots              git roots for the project
   * @param defaultRoot        a default root
   * @param gitRootChooser     git root selector
   * @param currentBranchLabel current branch label (might be null)
   */
  public static void setupRootChooser(final Project project,
                                      final List<VirtualFile> roots,
                                      final VirtualFile defaultRoot,
                                      final JComboBox gitRootChooser,
                                      @Nullable final JLabel currentBranchLabel) {
    for (VirtualFile root : roots) {
      gitRootChooser.addItem(root);
    }
    gitRootChooser.setRenderer(getVirtualFileListCellRenderer());
    gitRootChooser.setSelectedItem(defaultRoot);
    if (currentBranchLabel != null) {
      final ActionListener listener = new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          try {
            VirtualFile root = (VirtualFile)gitRootChooser.getSelectedItem();
            GitBranch current = GitBranch.current(project, root);
            assert currentBranchLabel != null;
            if (current == null) {
              currentBranchLabel.setText(NO_CURRENT_BRANCH);
            }
            else {
              currentBranchLabel.setText(current.getName());
            }
          }
          catch (VcsException ex) {
            GitVcs.getInstance(project).showErrors(Collections.singletonList(ex), GitBundle.getString("merge.retriving.branches"));
          }
        }
      };
      listener.actionPerformed(null);
      gitRootChooser.addActionListener(listener);
    }
  }

  /**
   * Show error associated with the specified operation
   *
   * @param project   the project
   * @param ex        the exception
   * @param operation the operation name
   */
  public static void showOperationError(final Project project, final VcsException ex, @NonNls @NotNull final String operation) {
    showOperationError(project, operation, ex.getMessage());
  }

  /**
   * Show error associated with the specified operation
   *
   * @param project   the project
   * @param message   the error description
   * @param operation the operation name
   */
  public static void showOperationError(final Project project, final String operation, final String message) {
    Messages.showErrorDialog(project, message, GitBundle.message("error.occurred.during", operation));
  }

  /**
   * Setup remotes combobox. The default remote for the current branch is selected by default.
   * This method gets current branch for the proejct.
   *
   * @param project        the project
   * @param root           the git root
   * @param remoteCombobox the combobox to update
   */
  public static void setupRemotes(final Project project, final VirtualFile root, final JComboBox remoteCombobox) {
    GitBranch gitBranch = null;
    try {
      gitBranch = GitBranch.current(project, root);
    }
    catch (VcsException ex) {
      // ingore error
    }
    final String branch = gitBranch != null ? gitBranch.getName() : null;
    setupRemotes(project, root, branch, remoteCombobox);

  }


  /**
   * Setup remotes combobox. The default remote for the current branch is selected by default.
   *
   * @param project        the project
   * @param root           the git root
   * @param currentBranch  the current branch
   * @param remoteCombobox the combobox to update
   */
  public static void setupRemotes(final Project project,
                                  final VirtualFile root,
                                  final String currentBranch,
                                  final JComboBox remoteCombobox) {
    try {
      List<GitRemote> remotes = GitRemote.list(project, root);
      String remote = null;
      if (currentBranch != null) {
        remote = GitConfigUtil.getValue(project, root, "branch." + currentBranch + ".remote");
      }
      remoteCombobox.setRenderer(getGitRemoteListCellRenderer(remote));
      GitRemote toSelect = null;
      remoteCombobox.removeAllItems();
      for (GitRemote r : remotes) {
        remoteCombobox.addItem(r);
        if (r.name().equals(remote)) {
          toSelect = r;
        }
      }
      if (toSelect != null) {
        remoteCombobox.setSelectedItem(toSelect);
      }
    }
    catch (VcsException e) {
      GitVcs.getInstance(project).showErrors(Collections.singletonList(e), GitBundle.getString("pull.retriving.remotes"));
    }
  }
}
