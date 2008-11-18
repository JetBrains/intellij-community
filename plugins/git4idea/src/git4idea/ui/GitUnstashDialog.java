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
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import git4idea.GitBranch;
import git4idea.GitRevisionNumber;
import git4idea.actions.GitShowAllSubmittedFilesAction;
import git4idea.commands.*;
import git4idea.i18n.GitBundle;
import git4idea.validators.GitBranchNameValidator;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashSet;
import java.util.List;

/**
 * The unstash dialog
 */
public class GitUnstashDialog extends DialogWrapper {
  /**
   * Git root selector
   */
  private JComboBox myGitRootComboBox;
  /**
   * The current branch label
   */
  private JLabel myCurrentBranch;
  /**
   * The view stash button
   */
  private JButton myViewButton;
  /**
   * The drop stash button
   */
  private JButton myDropButton;
  /**
   * The clear stashes button
   */
  private JButton myClearButton;
  /**
   * The pop stash checkbox
   */
  private JCheckBox myPopStashCheckBox;
  /**
   * The branch text field
   */
  private JTextField myBranchTextField;
  /**
   * The root panel of the dialog
   */
  private JPanel myPanel;
  /**
   * The stash list
   */
  private JList myStashList;
  /**
   * If this checkbox is selected, the index is reinstated as well as working tree
   */
  private JCheckBox myReinstateIndexCheckBox;
  /**
   * Set of branches for the current root
   */
  private final HashSet<String> myBranches = new HashSet<String>();

  /**
   * The project
   */
  private final Project myProject;

  /**
   * A constructor
   *
   * @param project     the project
   * @param roots       the list of the roots
   * @param defaultRoot the default root to select
   */
  public GitUnstashDialog(final Project project, final List<VirtualFile> roots, final VirtualFile defaultRoot) {
    super(project, true);
    myProject = project;
    setTitle(GitBundle.getString("unstash.title"));
    GitUIUtil.setupRootChooser(project, roots, defaultRoot, myGitRootComboBox, myCurrentBranch);
    myStashList.setModel(new DefaultListModel());
    refreshStashList();
    myGitRootComboBox.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        refreshStashList();
        updateDialogState();
      }
    });
    myStashList.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(final ListSelectionEvent e) {
        updateDialogState();
      }
    });
    myBranchTextField.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        updateDialogState();
      }
    });
    myClearButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        GitLineHandler h = new GitLineHandler(myProject, getGitRoot(), GitHandler.STASH);
        h.setNoSSH(true);
        h.addParameters("clear");
        GitHandlerUtil.doSynchronously(h, GitBundle.getString("unstash.clearing.stashes"), h.printableCommandLine());
        refreshStashList();
        updateDialogState();
      }
    });
    myDropButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        GitLineHandler h = new GitLineHandler(myProject, getGitRoot(), GitHandler.STASH);
        h.setNoSSH(true);
        final String stash = getSelectedStash();
        h.addParameters("drop", stash);
        GitHandlerUtil.doSynchronously(h, GitBundle.message("unstash.dropping.stash", stash), h.printableCommandLine());
        refreshStashList();
        updateDialogState();
      }
    });
    myViewButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        final VirtualFile root = getGitRoot();
        String stash;
        try {
          stash = GitRevisionNumber.resolve(myProject, root, getSelectedStash()).asString();
        }
        catch (VcsException ex) {
          GitUIUtil.showOperationError(myProject, ex, "resolving revision");
          return;
        }
        GitShowAllSubmittedFilesAction.showSubmittedFiles(myProject, stash, root);
      }
    });
    init();
    updateDialogState();
  }

  /**
   * Update state dialog depending on the current state of the fields
   */
  public void updateDialogState() {
    String branch = myBranchTextField.getText();
    if (branch.length() != 0) {
      myPopStashCheckBox.setEnabled(false);
      myPopStashCheckBox.setSelected(true);
      myReinstateIndexCheckBox.setEnabled(false);
      myReinstateIndexCheckBox.setSelected(true);
      if (!GitBranchNameValidator.INSTANCE.checkInput(branch)) {
        setErrorText(GitBundle.getString("unstash.error.invalid.branch.name"));
        setOKActionEnabled(false);
        return;
      }
      if (myBranches.contains(branch)) {
        setErrorText(GitBundle.getString("unstash.error.branch.exists"));
        setOKActionEnabled(false);
        return;
      }
    }
    else {
      if (!myPopStashCheckBox.isEnabled()) {
        myPopStashCheckBox.setSelected(false);
      }
      myPopStashCheckBox.setEnabled(true);
      if (!myReinstateIndexCheckBox.isEnabled()) {
        myReinstateIndexCheckBox.setSelected(false);
      }
      myReinstateIndexCheckBox.setEnabled(true);
    }
    if (myStashList.getModel().getSize() == 0) {
      myViewButton.setEnabled(false);
      myDropButton.setEnabled(false);
      myClearButton.setEnabled(false);
      setErrorText(null);
      setOKActionEnabled(false);
      return;
    }
    else {
      myClearButton.setEnabled(true);
    }
    if (myStashList.getSelectedIndex() == -1) {
      myViewButton.setEnabled(false);
      myDropButton.setEnabled(false);
      setErrorText(null);
      setOKActionEnabled(false);
      return;
    }
    else {
      myViewButton.setEnabled(true);
      myDropButton.setEnabled(true);
    }
    setErrorText(null);
    setOKActionEnabled(true);
  }

  /**
   * Refresh stash list
   */
  private void refreshStashList() {
    final DefaultListModel listModel = (DefaultListModel)myStashList.getModel();
    listModel.clear();
    GitSimpleHandler h = new GitSimpleHandler(myProject, getGitRoot(), GitHandler.STASH);
    h.setSilent(true);
    h.setNoSSH(true);
    h.addParameters("list");
    String out;
    try {
      out = h.run();
    }
    catch (VcsException e) {
      GitUIUtil.showOperationError(myProject, e, h.printableCommandLine());
      return;
    }
    for (StringScanner s = new StringScanner(out); s.hasMoreData();) {
      listModel.addElement(new StashInfo(s.boundedToken(':'), s.boundedToken(':'), s.line()));
    }
    myBranches.clear();
    try {
      GitBranch.list(myProject, getGitRoot(), false, true, myBranches);
    }
    catch (VcsException e) {
      // ignore error
    }
  }

  /**
   * @return the selected git root
   */
  public VirtualFile getGitRoot() {
    return (VirtualFile)myGitRootComboBox.getSelectedItem();
  }

  /**
   * @return unstash handler
   */
  public GitLineHandler handler() {
    GitLineHandler h = new GitLineHandler(myProject, getGitRoot(), GitHandler.STASH);
    String branch = myBranchTextField.getText();
    if (branch.length() == 0) {
      h.addParameters(myPopStashCheckBox.isSelected() ? "pop" : "apply");
      if (myReinstateIndexCheckBox.isSelected()) {
        h.addParameters("--index");
      }
    }
    else {
      h.addParameters("branch", branch);
    }
    h.addParameters(getSelectedStash());
    return h;
  }

  /**
   * @return selected stash
   * @throws NullPointerException if no stash is selected
   */
  private String getSelectedStash() {
    return ((StashInfo)myStashList.getSelectedValue()).myStash;
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
  protected String getDimensionServiceKey() {
    return getClass().getName();
  }

  /**
   * Stash information class
   */
  private static class StashInfo {
    /**
     * Stash name
     */
    private final String myStash;
    /**
     * The text representation
     */
    private final String myText;

    /**
     * A constructor
     *
     * @param stash   the stash name
     * @param branch  the branch name
     * @param message the stash message
     */
    public StashInfo(final String stash, final String branch, final String message) {
      myStash = stash;
      myText =
        GitBundle.message("unstash.stashes.item", StringUtil.escapeXml(stash), StringUtil.escapeXml(branch), StringUtil.escapeXml(message));
    }

    /**
     * @return string representation
     */
    @Override
    public String toString() {
      return myText;
    }
  }
}
