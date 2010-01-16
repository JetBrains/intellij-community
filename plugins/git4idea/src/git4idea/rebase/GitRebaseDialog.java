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
package git4idea.rebase;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import git4idea.GitBranch;
import git4idea.GitTag;
import git4idea.commands.GitCommand;
import git4idea.commands.GitLineHandler;
import git4idea.config.GitConfigUtil;
import git4idea.i18n.GitBundle;
import git4idea.merge.GitMergeUtil;
import git4idea.ui.GitReferenceValidator;
import git4idea.ui.GitUIUtil;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

/**
 * The dialog that allows initiating git rebase activity
 */
public class GitRebaseDialog extends DialogWrapper {
  /**
   * Git root selector
   */
  private JComboBox myGitRootComboBox;
  /**
   * The selector for branch to rebase
   */
  private JComboBox myBranchComboBox;
  /**
   * The from branch combo box. This is used as base branch if different from onto branch
   */
  private JComboBox myFromComboBox;
  /**
   * The validation button for from branch
   */
  private JButton myFromValidateButton;
  /**
   * The onto branch combobox.
   */
  private JComboBox myOntoComboBox;
  /**
   * The validate button for onto branch
   */
  private JButton myOntoValidateButton;
  /**
   * Show tags in drop down
   */
  private JCheckBox myShowTagsCheckBox;
  /**
   * Merge strategy drop down
   */
  private JComboBox myMergeStrategyComboBox;
  /**
   * If selected, rebase is interactive
   */
  private JCheckBox myInteractiveCheckBox;
  /**
   * No merges are performed if selected.
   */
  private JCheckBox myDoNotUseMergeCheckBox;
  /**
   * The root panel of the dialog
   */
  private JPanel myPanel;
  /**
   * If selected, remote branches are shown as well
   */
  private JCheckBox myShowRemoteBranchesCheckBox;
  /**
   * Preserve merges checkbox
   */
  private JCheckBox myPreserveMergesCheckBox;
  /**
   * The current project
   */
  private final Project myProject;
  /**
   * The list of local branches
   */
  private final List<GitBranch> myLocalBranches = new ArrayList<GitBranch>();
  /**
   * The list of remote branches
   */
  private final List<GitBranch> myRemoteBranches = new ArrayList<GitBranch>();
  /**
   * The current branch
   */
  private GitBranch myCurrentBranch;
  /**
   * The tags
   */
  private final List<GitTag> myTags = new ArrayList<GitTag>();
  /**
   * The validator for onto field
   */
  private final GitReferenceValidator myOntoValidator;
  /**
   * The validator for from field
   */
  private final GitReferenceValidator myFromValidator;

  /**
   * A constructor
   *
   * @param project     a project to select
   * @param roots       a git repository roots for the project
   * @param defaultRoot a guessed default root
   */
  public GitRebaseDialog(Project project, List<VirtualFile> roots, VirtualFile defaultRoot) {
    super(project, true);
    setTitle(GitBundle.getString("rebase.title"));
    setOKButtonText(GitBundle.getString("rebase.button"));
    init();
    myProject = project;
    final Runnable validateRunnable = new Runnable() {
      public void run() {
        validateFields();
      }
    };
    myOntoValidator = new GitReferenceValidator(myProject, myGitRootComboBox, GitUIUtil.getTextField(myOntoComboBox), myOntoValidateButton,
                                                validateRunnable);
    myFromValidator = new GitReferenceValidator(myProject, myGitRootComboBox, GitUIUtil.getTextField(myFromComboBox), myFromValidateButton,
                                                validateRunnable);
    GitUIUtil.setupRootChooser(myProject, roots, defaultRoot, myGitRootComboBox, null);
    myGitRootComboBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        validateFields();
      }
    });
    setupBranches();
    setupStrategy();
    validateFields();
  }

  public GitLineHandler handler() {
    GitLineHandler h = new GitLineHandler(myProject, gitRoot(), GitCommand.REBASE);
    h.setNoSSH(true);
    if (myInteractiveCheckBox.isSelected() && myInteractiveCheckBox.isEnabled()) {
      h.addParameters("-i");
    }
    h.addParameters("-v");
    if (!myDoNotUseMergeCheckBox.isSelected()) {
      if (myMergeStrategyComboBox.getSelectedItem().equals(GitMergeUtil.DEFAULT_STRATEGY)) {
        h.addParameters("-m");
      }
      else {
        h.addParameters("-s", myMergeStrategyComboBox.getSelectedItem().toString());
      }
    }
    if (myPreserveMergesCheckBox.isSelected()) {
      h.addParameters("-p");
    }
    String from = GitUIUtil.getTextField(myFromComboBox).getText();
    String onto = GitUIUtil.getTextField(myOntoComboBox).getText();
    if (from.length() == 0) {
      h.addParameters(onto);
    }
    else {
      h.addParameters("--onto", onto, from);
    }
    final String selectedBranch = (String)myBranchComboBox.getSelectedItem();
    if (myCurrentBranch != null && !myCurrentBranch.getName().equals(selectedBranch)) {
      h.addParameters(selectedBranch);
    }
    return h;
  }

  /**
   * Setup strategy
   */
  private void setupStrategy() {
    for (String s : GitMergeUtil.getMergeStrategies(1)) {
      myMergeStrategyComboBox.addItem(s);
    }
    myMergeStrategyComboBox.setSelectedItem(GitMergeUtil.DEFAULT_STRATEGY);
    myDoNotUseMergeCheckBox.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        myMergeStrategyComboBox.setEnabled(!myDoNotUseMergeCheckBox.isSelected());
      }
    });
  }


  /**
   * Validate fields
   */
  private void validateFields() {
    if (GitUIUtil.getTextField(myOntoComboBox).getText().length() == 0) {
      setErrorText(null);
      setOKActionEnabled(false);
      return;
    }
    else if (myOntoValidator.isInvalid()) {
      setErrorText(GitBundle.getString("rebase.invalid.onto"));
      setOKActionEnabled(false);
      return;
    }
    if (GitUIUtil.getTextField(myFromComboBox).getText().length() != 0 && myFromValidator.isInvalid()) {
      setErrorText(GitBundle.getString("rebase.invalid.from"));
      setOKActionEnabled(false);
      return;
    }
    if (GitRebaseUtils.isRebaseInTheProgress(gitRoot())) {
      setErrorText(GitBundle.getString("rebase.in.progress"));
      setOKActionEnabled(false);
      return;
    }
    setErrorText(null);
    setOKActionEnabled(true);
  }

  /**
   * Setup branch drop down.
   */
  private void setupBranches() {
    GitUIUtil.getTextField(myOntoComboBox).getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        validateFields();
      }
    });
    final ActionListener rootListener = new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        loadRefs();
        updateBranches();
      }
    };
    final ActionListener showListener = new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        updateOntoFrom();
      }
    };
    myShowRemoteBranchesCheckBox.addActionListener(showListener);
    myShowTagsCheckBox.addActionListener(showListener);
    rootListener.actionPerformed(null);
    myGitRootComboBox.addActionListener(rootListener);
    myBranchComboBox.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        updateTrackedBranch();
      }
    });
  }

  /**
   * Update branches when git root changed
   */
  private void updateBranches() {
    myBranchComboBox.removeAllItems();
    for (GitBranch b : myLocalBranches) {
      myBranchComboBox.addItem(b.getName());
    }
    if (myCurrentBranch != null) {
      myBranchComboBox.setSelectedItem(myCurrentBranch.getName());
    }
    else {
      myBranchComboBox.setSelectedItem(0);
    }
    updateOntoFrom();
    updateTrackedBranch();
  }

  /**
   * Update onto and from comboboxes.
   */
  private void updateOntoFrom() {
    String onto = GitUIUtil.getTextField(myOntoComboBox).getText();
    String from = GitUIUtil.getTextField(myFromComboBox).getText();
    myFromComboBox.removeAllItems();
    myOntoComboBox.removeAllItems();
    for (GitBranch b : myLocalBranches) {
      myFromComboBox.addItem(b);
      myOntoComboBox.addItem(b);
    }
    if (myShowRemoteBranchesCheckBox.isSelected()) {
      for (GitBranch b : myRemoteBranches) {
        myFromComboBox.addItem(b);
        myOntoComboBox.addItem(b);
      }
    }
    if (myShowTagsCheckBox.isSelected()) {
      for (GitTag t : myTags) {
        myFromComboBox.addItem(t);
        myOntoComboBox.addItem(t);
      }
    }
    GitUIUtil.getTextField(myOntoComboBox).setText(onto);
    GitUIUtil.getTextField(myFromComboBox).setText(from);
  }

  /**
   * Load tags and branches
   */
  private void loadRefs() {
    try {
      myLocalBranches.clear();
      myRemoteBranches.clear();
      myTags.clear();
      final VirtualFile root = gitRoot();
      GitBranch.list(myProject, root, true, false, myLocalBranches);
      GitBranch.list(myProject, root, false, true, myRemoteBranches);
      GitTag.list(myProject, root, myTags);
      myCurrentBranch = GitBranch.current(myProject, root);
    }
    catch (VcsException e) {
      GitUIUtil.showOperationError(myProject, e, "git branch -a");
    }
  }

  /**
   * Update tracked branch basing on the currently selected branch
   */
  private void updateTrackedBranch() {
    try {
      final VirtualFile root = gitRoot();
      String currentBranch = (String)myBranchComboBox.getSelectedItem();
      final GitBranch trackedBranch;
      if (currentBranch != null) {
        String remote = GitConfigUtil.getValue(myProject, root, "branch." + currentBranch + ".remote");
        String merge = GitConfigUtil.getValue(myProject, root, "branch." + currentBranch + ".merge");
        String name =
          (merge != null && merge.startsWith(GitBranch.REFS_HEADS_PREFIX)) ? merge.substring(GitBranch.REFS_HEADS_PREFIX.length()) : null;
        if (remote == null || merge == null || name == null) {
          trackedBranch = null;
        }
        else {
          if (remote.equals(".")) {
            trackedBranch = new GitBranch(name, false, false);
          }
          else {
            trackedBranch = new GitBranch(remote + "/" + name, false, true);
          }
        }
      }
      else {
        trackedBranch = null;
      }
      if (trackedBranch != null) {
        myOntoComboBox.setSelectedItem(trackedBranch);
      }
      else {
        GitUIUtil.getTextField(myOntoComboBox).setText("");
      }
      GitUIUtil.getTextField(myFromComboBox).setText("");
    }
    catch (VcsException e) {
      GitUIUtil.showOperationError(myProject, e, "git config");
    }
  }

  /**
   * @return the currently selected git root
   */
  public VirtualFile gitRoot() {
    return (VirtualFile)myGitRootComboBox.getSelectedItem();
  }


  /**
   * {@inheritDoc}
   */
  @Override
  protected String getDimensionServiceKey() {
    return getClass().getName();
  }

  /**
   * {@inheritDoc}
   */
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected String getHelpId() {
    return "reference.VersionControl.Git.Rebase";
  }
}
