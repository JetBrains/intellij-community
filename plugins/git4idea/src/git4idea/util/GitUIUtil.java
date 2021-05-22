// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SimpleListCellRenderer;
import git4idea.GitBranch;
import git4idea.GitUtil;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Utilities for git plugin user interface
 */
public final class GitUIUtil {

  /**
   * A private constructor for utility class
   */
  private GitUIUtil() {
  }

  /**
   * @deprecated use {@link VcsNotifier} instead
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public static void notifyError(Project project,
                                 @Nls @NotNull String title,
                                 @Nls @Nullable String description,
                                 boolean important,
                                 @Nullable Exception error) {
    if (important) {
      VcsNotifier.getInstance(project)
        .notifyError(null, title, StringUtil.notNullize(description), Collections.singleton(error));
    }
    else {
      VcsNotifier.getInstance(project)
        .notifyImportantWarning(null, title, StringUtil.notNullize(description), Collections.singleton(error));
    }
  }

  /**
   * Get text field from combobox
   *
   * @param comboBox a combobox to examine
   * @return the text field reference
   */
  @SuppressWarnings("rawtypes")
  public static JTextField getTextField(JComboBox comboBox) {
    return (JTextField)comboBox.getEditor().getEditorComponent();
  }

  /**
   * Setup root chooser with specified elements and link selection to the current branch label.
   *
   * @param project            a context project
   * @param roots              git roots for the project
   * @param defaultRoot        a default root
   * @param gitRootChooser     git root selector
   * @param currentBranchLabel current branch label (might be null)
   */
  @SuppressWarnings({"rawtypes", "unchecked"})
  public static void setupRootChooser(@NotNull final Project project,
                                      @NotNull final List<? extends VirtualFile> roots,
                                      @Nullable final VirtualFile defaultRoot,
                                      @NotNull final JComboBox gitRootChooser,
                                      @Nullable final JLabel currentBranchLabel) {
    for (VirtualFile root : roots) {
      gitRootChooser.addItem(root);
    }
    gitRootChooser.setRenderer(SimpleListCellRenderer.create(GitBundle.message("combobox.item.file.invalid"),
                                                             VirtualFile::getPresentableUrl));
    gitRootChooser.setSelectedItem(defaultRoot != null ? defaultRoot : roots.get(0));
    if (currentBranchLabel != null) {
      final ActionListener listener = new ActionListener() {
        @Override
        public void actionPerformed(final ActionEvent e) {
          VirtualFile root = (VirtualFile)gitRootChooser.getSelectedItem();
          assert root != null : "The root must not be null";
          GitRepository repo = GitUtil.getRepositoryManager(project).getRepositoryForRootQuick(root);
          assert repo != null : "The repository must not be null";
          GitBranch current = repo.getCurrentBranch();
          if (current == null) {
            currentBranchLabel.setText(getNoCurrentBranch());
          }
          else {
            currentBranchLabel.setText(current.getName());
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
  public static void showOperationError(final Project project, final VcsException ex, @Nls @NotNull final String operation) {
    showOperationError(project, operation, ex.getMessage());
  }

  /**
   * Show errors associated with the specified operation
   *
   * @param project   the project
   * @param exs       the exceptions to show
   * @param operation the operation name
   */
  public static void showOperationErrors(final Project project,
                                         final Collection<? extends VcsException> exs,
                                         @Nls @NotNull final String operation) {
    if (exs.size() == 1) {
      showOperationError(project, operation, exs.iterator().next().getMessage());
    }
    else if (exs.size() > 1) {
      // TODO use dialog in order to show big messages
      StringBuilder b = new StringBuilder();
      for (VcsException ex : exs) {
        b.append(GitBundle.message("errors.message.item", ex.getMessage()));
      }
      showOperationError(project, operation, GitBundle.message("errors.message", b.toString()));
    }
  }

  /**
   * Show error associated with the specified operation
   *
   * @param project   the project
   * @param message   the error description
   * @param operation the operation name
   */
  public static void showOperationError(final Project project, final String operation, @Nls final String message) {
    Messages.showErrorDialog(project, message, GitBundle.message("error.occurred.during", operation));
  }

  /**
   * Declares states for two checkboxes to be mutually exclusive. When one of the checkboxes goes to the specified state, other is
   * disabled and forced into reverse of the state (to prevent very fast users from selecting incorrect state or incorrect
   * initial configuration).
   *
   * @param first       the first checkbox
   * @param firstState  the state of the first checkbox
   * @param second      the second checkbox
   * @param secondState the state of the second checkbox
   */
  public static void exclusive(final JCheckBox first, final boolean firstState, final JCheckBox second, final boolean secondState) {
    ActionListener l = new ActionListener() {
      /**
       * One way check for the condition
       * @param checked the first to check
       * @param checkedState the state to match
       * @param changed the changed control
       * @param impliedState the implied state
       */
      private void check(final JCheckBox checked, final boolean checkedState, final JCheckBox changed, final boolean impliedState) {
        if (checked.isSelected() == checkedState) {
          changed.setSelected(impliedState);
          changed.setEnabled(false);
        }
        else {
          changed.setEnabled(true);
        }
      }

      /**
       * {@inheritDoc}
       */
      @Override
      public void actionPerformed(ActionEvent e) {
        check(first, firstState, second, !secondState);
        check(second, secondState, first, !firstState);
      }
    };
    first.addActionListener(l);
    second.addActionListener(l);
    l.actionPerformed(null);
  }

  public static String bold(String s) {
    return surround(s, "b");
  }

  public static String code(String s) {
    return surround(s, "code");
  }

  private static String surround(String s, String tag) {
    return String.format("<%2$s>%1$s</%2$s>", s, tag);
  }

  @Nls
  public static String getNoCurrentBranch() {
    return GitBundle.message("common.no.active.branch");
  }
}
