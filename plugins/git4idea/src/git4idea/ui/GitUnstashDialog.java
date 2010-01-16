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
package git4idea.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import git4idea.GitBranch;
import git4idea.GitRevisionNumber;
import git4idea.actions.GitShowAllSubmittedFilesAction;
import git4idea.commands.*;
import git4idea.config.GitConfigUtil;
import git4idea.i18n.GitBundle;
import git4idea.validators.GitBranchNameValidator;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

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
    setOKButtonText(GitBundle.getString("unstash.button.apply"));
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
    myPopStashCheckBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updateDialogState();
      }
    });
    myClearButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        GitLineHandler h = new GitLineHandler(myProject, getGitRoot(), GitCommand.STASH);
        h.setNoSSH(true);
        h.addParameters("clear");
        GitHandlerUtil.doSynchronously(h, GitBundle.getString("unstash.clearing.stashes"), h.printableCommandLine());
        refreshStashList();
        updateDialogState();
      }
    });
    myDropButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        final String stash = getSelectedStash();
        GitSimpleHandler h = dropHandler(stash);
        try {
          h.setSilent(true);
          h.run();
          h.unsilence();
        }
        catch (VcsException ex) {
          try {
            //noinspection HardCodedStringLiteral
            if (ex.getMessage().startsWith("fatal: Needed a single revision")) {
              h = dropHandler(translateStash(stash));
              h.run();
            }
            else {
              h.unsilence();
              throw ex;
            }
          }
          catch (VcsException ex2) {
            GitUIUtil.showOperationError(myProject, ex, h.printableCommandLine());
            return;
          }
        }
        refreshStashList();
        updateDialogState();
      }

      private GitSimpleHandler dropHandler(String stash) {
        GitSimpleHandler h = new GitSimpleHandler(myProject, getGitRoot(), GitCommand.STASH);
        h.setNoSSH(true);
        h.addParameters("drop", stash);
        return h;
      }
    });
    myViewButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        final VirtualFile root = getGitRoot();
        String resolvedStash;
        String selectedStash = getSelectedStash();
        try {
          resolvedStash = GitRevisionNumber.resolve(myProject, root, selectedStash).asString();
        }
        catch (VcsException ex) {
          try {
            //noinspection HardCodedStringLiteral
            if (ex.getMessage().startsWith("fatal: bad revision 'stash@")) {
              selectedStash = translateStash(selectedStash);
              resolvedStash = GitRevisionNumber.resolve(myProject, root, selectedStash).asString();
            }
            else {
              throw ex;
            }
          }
          catch (VcsException ex2) {
            GitUIUtil.showOperationError(myProject, ex, "resolving revision");
            return;
          }
        }
        GitShowAllSubmittedFilesAction.showSubmittedFiles(myProject, resolvedStash, root);
      }
    });
    init();
    updateDialogState();
  }

  /**
   * Translate stash name so that { } are escaped.
   *
   * @param selectedStash a selected stash
   * @return translated name
   */
  private static String translateStash(String selectedStash) {
    return selectedStash.replaceAll("([\\{}])", "\\\\$1");
  }

  /**
   * Update state dialog depending on the current state of the fields
   */
  private void updateDialogState() {
    String branch = myBranchTextField.getText();
    if (branch.length() != 0) {
      setOKButtonText(GitBundle.getString("unstash.button.branch"));
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
      setOKButtonText(
        myPopStashCheckBox.isSelected() ? GitBundle.getString("unstash.button.pop") : GitBundle.getString("unstash.button.apply"));
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
    GitSimpleHandler h = new GitSimpleHandler(myProject, getGitRoot(), GitCommand.STASH);
    h.setSilent(true);
    h.setNoSSH(true);
    h.addParameters("list");
    String out;
    try {
      h.setCharset(Charset.forName(GitConfigUtil.getLogEncoding(myProject, getGitRoot())));
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
      GitBranch.listAsStrings(myProject, getGitRoot(), false, true, myBranches);
    }
    catch (VcsException e) {
      // ignore error
    }
  }

  /**
   * @return the selected git root
   */
  private VirtualFile getGitRoot() {
    return (VirtualFile)myGitRootComboBox.getSelectedItem();
  }

  /**
   * @param escaped if true stash name will be escaped
   * @return unstash handler
   */
  private GitLineHandler handler(boolean escaped) {
    GitLineHandler h = new GitLineHandler(myProject, getGitRoot(), GitCommand.STASH);
    h.setNoSSH(true);
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
    final String selectedStash = getSelectedStash();
    h.addParameters(escaped ? translateStash(selectedStash) : selectedStash);
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
   * {@inheritDoc}
   */
  @Override
  protected String getHelpId() {
    return "reference.VersionControl.Git.Unstash";
  }

  /**
   * Show unstash dialog and process its result
   *
   * @param project       the context project
   * @param gitRoots      the git roots
   * @param defaultRoot   the default git root
   * @param affectedRoots the affected roots
   */
  public static void showUnstashDialog(Project project,
                                       List<VirtualFile> gitRoots,
                                       VirtualFile defaultRoot,
                                       Set<VirtualFile> affectedRoots) {
    GitUnstashDialog d = new GitUnstashDialog(project, gitRoots, defaultRoot);
    d.show();
    if (!d.isOK()) {
      return;
    }
    affectedRoots.add(d.getGitRoot());
    GitLineHandler h = d.handler(false);
    final AtomicBoolean needToEscapedBraces = new AtomicBoolean(false);
    h.addLineListener(new GitLineHandlerAdapter() {
      public void onLineAvailable(String line, Key outputType) {
        if (line.startsWith("fatal: Needed a single revision")) {
          needToEscapedBraces.set(true);
        }
      }
    });
    int rc = GitHandlerUtil.doSynchronously(h, GitBundle.getString("unstash.unstashing"), h.printableCommandLine(), false);
    if (needToEscapedBraces.get()) {
      h = d.handler(true);
      rc = GitHandlerUtil.doSynchronously(h, GitBundle.getString("unstash.unstashing"), h.printableCommandLine(), false);
    }
    if (rc != 0) {
      GitUIUtil.showOperationErrors(project, h.errors(), h.printableCommandLine());
    }
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
