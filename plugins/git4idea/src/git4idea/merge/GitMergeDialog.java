// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.merge;

import com.intellij.ide.util.ElementsChooser;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitVcs;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitLineHandler;
import git4idea.i18n.GitBundle;
import git4idea.util.GitUIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collections;
import java.util.List;
import java.util.StringTokenizer;

/**
 * A dialog for merge action. It represents most options available for git merge.
 */
public class GitMergeDialog extends DialogWrapper {
  /**
   * The git root available for git merge action
   */
  private JComboBox myGitRoot;
  /**
   * The check box indicating that no commit will be created
   */
  private JCheckBox myNoCommitCheckBox;
  /**
   * The checkbox that suppresses fast forward resolution even if it is available
   */
  private JCheckBox myNoFastForwardCheckBox;
  /**
   * The checkbox that allows squashing all changes from branch into a single commit
   */
  private JCheckBox mySquashCommitCheckBox;
  /**
   * The label containing a name of the current branch
   */
  private JLabel myCurrentBranchText;
  /**
   * The panel containing a chooser of branches to merge
   */
  private JPanel myBranchToMergeContainer;
  /**
   * Chooser of branches to merge
   */
  private ElementsChooser<String> myBranchChooser;
  /**
   * The commit message
   */
  private JTextField myCommitMessage;
  /**
   * The strategy for merge
   */
  private JComboBox myStrategy;
  /**
   * The panel
   */
  private JPanel myPanel;
  /**
   * The log information checkbox
   */
  private JCheckBox myAddLogInformationCheckBox;

  @NotNull private final Project myProject;
  private final GitVcs myVcs;

  public GitMergeDialog(@NotNull Project project, List<VirtualFile> roots, VirtualFile defaultRoot) {
    super(project, true);
    setTitle(GitBundle.getString("merge.branch.title"));
    myProject = project;
    myVcs = GitVcs.getInstance(project);
    initBranchChooser();
    setOKActionEnabled(false);
    setOKButtonText(GitBundle.getString("merge.branch.button"));
    GitUIUtil.setupRootChooser(myProject, roots, defaultRoot, myGitRoot, myCurrentBranchText);
    GitUIUtil.imply(mySquashCommitCheckBox, true, myNoCommitCheckBox, true);
    GitUIUtil.imply(mySquashCommitCheckBox, true, myAddLogInformationCheckBox, false);
    GitUIUtil.implyDisabled(mySquashCommitCheckBox, true, myCommitMessage);
    GitUIUtil.exclusive(mySquashCommitCheckBox, true, myNoFastForwardCheckBox, true);
    myGitRoot.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent event) {
        try {
          updateBranches();
        }
        catch (VcsException ex) {
          myVcs.showErrors(Collections.singletonList(ex), GitBundle.getString("merge.retrieving.branches"));
        }
      }
    });
    init();
  }

  private void initBranchChooser() {
    myBranchChooser = new ElementsChooser<>(true);
    myBranchChooser.setToolTipText(GitBundle.getString("merge.branches.tooltip"));
    GridBagConstraints c = new GridBagConstraints();
    c.insets = new Insets(0, 0, 0, 0);
    c.gridx = 0;
    c.gridy = 0;
    c.weightx = 1;
    c.weighty = 1;
    c.fill = GridBagConstraints.BOTH;
    myBranchToMergeContainer.add(myBranchChooser, c);
    GitMergeUtil.setupStrategies(myBranchChooser, myStrategy);
    final ElementsChooser.ElementsMarkListener<String> listener = new ElementsChooser.ElementsMarkListener<String>() {
      @Override
      public void elementMarkChanged(final String element, final boolean isMarked) {
        setOKActionEnabled(getSelectedBranches().size() != 0);
      }
    };
    listener.elementMarkChanged(null, true);
    myBranchChooser.addElementsMarkListener(listener);
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myBranchChooser.getComponent();
  }

  /**
   * Setup branches for git root, this method should be called when root is changed.
   */
  public void updateBranches() throws VcsException {
    VirtualFile root = getSelectedRoot();
    GitLineHandler handler = new GitLineHandler(myProject, root, GitCommand.BRANCH);
    handler.addParameters("--no-color", "-a", "--no-merged");
    String output = ProgressManager.getInstance().runProcessWithProgressSynchronously(
      () -> Git.getInstance().runCommand(handler).getOutputOrThrow(),
      "Preparing List of Branches", true, myProject);
    myBranchChooser.clear();
    for (StringTokenizer lines = new StringTokenizer(output, "\n", false); lines.hasMoreTokens();) {
      String branch = lines.nextToken().substring(2);
      myBranchChooser.addElement(branch, false);
    }
  }

  /**
   * @return get line handler configured according to the selected options
   */
  public GitLineHandler handler() {
    if (!isOK()) {
      throw new IllegalStateException("The handler could be retrieved only if dialog was completed successfully.");
    }
    VirtualFile root = (VirtualFile)myGitRoot.getSelectedItem();
    GitLineHandler h = new GitLineHandler(myProject, root, GitCommand.MERGE);
    if (myNoCommitCheckBox.isSelected()) {
      h.addParameters("--no-commit");
    }
    if (myAddLogInformationCheckBox.isSelected()) {
      h.addParameters("--log");
    }
    final String msg = myCommitMessage.getText().trim();
    if (msg.length() != 0) {
      h.addParameters("-m", msg);
    }
    if (mySquashCommitCheckBox.isSelected()) {
      h.addParameters("--squash");
    }
    if (myNoFastForwardCheckBox.isSelected()) {
      h.addParameters("--no-ff");
    }
    String strategy = (String)myStrategy.getSelectedItem();
    if (!GitMergeUtil.getDefaultStrategy().equals(strategy)) {
      h.addParameters("--strategy", strategy);
    }
    for (String branch : getSelectedBranches()) {
      h.addParameters(branch);
    }
    return h;
  }

  @NotNull
  public List<String> getSelectedBranches() {
    return myBranchChooser.getMarkedElements();
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  protected String getDimensionServiceKey() {
    return getClass().getName();
  }

  @Override
  protected String getHelpId() {
    return "reference.VersionControl.Git.MergeBranches";
  }

  public VirtualFile getSelectedRoot() {
    return (VirtualFile)myGitRoot.getSelectedItem();
  }

  public boolean isCommitAfterMerge() {
    return !myNoCommitCheckBox.isSelected();
  }
}
