// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ui;

import com.intellij.CommonBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import git4idea.GitStashUsageCollector;
import git4idea.GitUtil;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRepository;
import git4idea.stash.GitStashOperations;
import git4idea.stash.GitStashUtils;
import git4idea.util.GitUIUtil;
import git4idea.validators.GitBranchValidatorKt;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

import static java.util.Collections.singletonList;

/**
 * The unstash dialog
 */
public class GitUnstashDialog extends DialogWrapper {
  private JComboBox<VirtualFile> myGitRootComboBox;
  private JLabel myCurrentBranch;
  private JButton myViewButton;
  private JButton myDropButton;
  private JButton myClearButton;
  private JCheckBox myPopStashCheckBox;
  private JTextField myBranchTextField;
  private JPanel myPanel;
  private JList<StashInfo> myStashList;
  private final DefaultListModel<StashInfo> myStashListModel;
  private JCheckBox myReinstateIndexCheckBox;

  private final Project myProject;
  private static final Logger LOG = Logger.getInstance(GitUnstashDialog.class);

  public GitUnstashDialog(final Project project, final List<VirtualFile> roots, final VirtualFile defaultRoot) {
    super(project, true);
    setModal(false);
    myProject = project;
    setTitle(GitBundle.message("unstash.title"));
    setOKButtonText(GitBundle.message("unstash.button.apply"));
    setCancelButtonText(CommonBundle.getCloseButtonText());
    GitUIUtil.setupRootChooser(project, roots, defaultRoot, myGitRootComboBox, myCurrentBranch);
    myStashListModel = new DefaultListModel<>();
    myStashList.setModel(myStashListModel);
    refreshStashList();
    myGitRootComboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        refreshStashList();
        updateDialogState();
      }
    });
    myStashList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(final ListSelectionEvent e) {
        updateDialogState();
      }
    });
    myBranchTextField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull final DocumentEvent e) {
        updateDialogState();
      }
    });
    myPopStashCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateDialogState();
      }
    });
    myClearButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        boolean cleared = GitStashOperations.clearStashesWithConfirmation(project, getGitRoot(), GitUnstashDialog.this.getContentPane());
        if (cleared) {
          refreshStashList();
          updateDialogState();
        }
      }
    });
    myDropButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        boolean dropped = GitStashOperations.dropStashWithConfirmation(myProject, GitUnstashDialog.this.getContentPane(),
                                                                       getSelectedStash());
        if (dropped) {
          refreshStashList();
          updateDialogState();
        }
      }
    });
    myViewButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        GitStashOperations.viewStash(project, getSelectedStash(), true);
      }
    });
    init();
    updateDialogState();
  }

  /**
   * Update state dialog depending on the current state of the fields
   */
  private void updateDialogState() {
    String branch = myBranchTextField.getText();
    if (branch.length() != 0) {
      setOKButtonText(GitBundle.message("unstash.button.branch"));
      myPopStashCheckBox.setEnabled(false);
      myPopStashCheckBox.setSelected(true);
      myReinstateIndexCheckBox.setEnabled(false);
      myReinstateIndexCheckBox.setSelected(true);
      GitRepository repository = GitUtil.getRepositoryManager(myProject).getRepositoryForRootQuick(getGitRoot());
      if (repository != null) {
        ValidationInfo branchValidationInfo = GitBranchValidatorKt.validateName(singletonList(repository), branch);
        if (branchValidationInfo != null) {
          setErrorText(branchValidationInfo.message, myBranchTextField);
          setOKActionEnabled(false);
          return;
        }
      }
    }
    else {
      if (!myPopStashCheckBox.isEnabled()) {
        myPopStashCheckBox.setSelected(false);
      }
      myPopStashCheckBox.setEnabled(true);
      setOKButtonText(
        myPopStashCheckBox.isSelected() ? GitBundle.message("unstash.button.pop") : GitBundle.message("unstash.button.apply"));
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

  private void refreshStashList() {
    myStashListModel.clear();
    VirtualFile root = getGitRoot();
    try {
      List<StashInfo> listOfStashes = ProgressManager.getInstance().runProcessWithProgressSynchronously(
        () -> GitStashUtils.loadStashStack(myProject, root),
        GitBundle.message("unstash.dialog.stash.list.load.progress.indicator.title"),
        true,
        myProject
      );

      for (StashInfo info : listOfStashes) {
        myStashListModel.addElement(info);
      }
      myStashList.setSelectedIndex(0);
    }
    catch (VcsException e) {
      LOG.warn(e);
      Messages.showErrorDialog(myProject, e.getMessage(), GitBundle.message("unstash.dialog.show.stashes.error.dialog.title"));
    }
  }

  private VirtualFile getGitRoot() {
    return (VirtualFile)myGitRootComboBox.getSelectedItem();
  }

  private StashInfo getSelectedStash() {
    return myStashList.getSelectedValue();
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
    return "reference.VersionControl.Git.Unstash";
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myStashList;
  }

  @Override
  protected void doOKAction() {
    GitStashUsageCollector.logStashPopDialog(!StringUtil.isEmpty(myBranchTextField.getText()),
                                             myReinstateIndexCheckBox.isSelected(),
                                             myPopStashCheckBox.isSelected());

    boolean completed = GitStashOperations.unstash(myProject, getSelectedStash(), myBranchTextField.getText(),
                                                   myPopStashCheckBox.isSelected(), myReinstateIndexCheckBox.isSelected());
    if (completed) {
      super.doOKAction();
    }
  }

  public static void showUnstashDialog(Project project, List<VirtualFile> gitRoots, VirtualFile defaultRoot) {
    new GitUnstashDialog(project, gitRoots, defaultRoot).show();
    // d is not modal=> everything else in doOKAction.
  }
}
