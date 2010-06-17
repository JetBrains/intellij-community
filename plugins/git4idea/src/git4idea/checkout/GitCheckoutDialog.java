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
package git4idea.checkout;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import git4idea.GitBranch;
import git4idea.GitTag;
import git4idea.GitVcs;
import git4idea.commands.GitCommand;
import git4idea.commands.GitLineHandler;
import git4idea.commands.GitSimpleHandler;
import git4idea.config.GitVcsSettings;
import git4idea.i18n.GitBundle;
import git4idea.ui.GitReferenceValidator;
import git4idea.ui.GitUIUtil;
import git4idea.validators.GitBranchNameValidator;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/**
 * Checkout dialog. It also allows checking out a new branch.
 */
public class GitCheckoutDialog extends DialogWrapper {
  /**
   * The root panel
   */
  private JPanel myPanel;
  /**
   * Git root field
   */
  private JComboBox myGitRoot;
  /**
   * Branch/tag to check out
   */
  private JComboBox myBranchToCkeckout;
  /**
   * Current branch
   */
  private JLabel myCurrentBranch;
  /**
   * Checkbox that specifies whether tags are included into drop down
   */
  private JCheckBox myIncludeTagsCheckBox;
  /**
   * The name of new branch
   */
  private JTextField myNewBranchName;
  /**
   * The delete branch before checkout flag
   */
  private JCheckBox myOverrideCheckBox;
  /**
   * The create reference log checkbox
   */
  private JCheckBox myCreateRefLogCheckBox;
  /**
   * The track branch checkbox
   */
  private JCheckBox myTrackBranchCheckBox;
  /**
   * The validator for branch to checkout
   */
  private final GitReferenceValidator myBranchToCkeckoutValidator;
  /**
   * The validate button
   */
  private JButton myValidateButton;
  /**
   * The context project
   */
  private final Project myProject;
  /**
   * The Git setting for the project
   */
  private final GitVcsSettings mySettings;
  /**
   * Existing branches for the currently selected root
   */
  private final HashSet<String> existingBranches = new HashSet<String>();

  /**
   * A constructor
   *
   * @param project     the context project
   * @param roots       the git roots for the project
   * @param defaultRoot the default root
   */
  public GitCheckoutDialog(Project project, List<VirtualFile> roots, VirtualFile defaultRoot) {
    super(project, true);
    setTitle(GitBundle.getString("checkout.branch"));
    myProject = project;
    mySettings = GitVcsSettings.getInstance(myProject);
    GitUIUtil.setupRootChooser(myProject, roots, defaultRoot, myGitRoot, myCurrentBranch);
    setupIncludeTags();
    setupBranches();
    setOKButtonText(GitBundle.getString("checkout.branch"));
    myBranchToCkeckoutValidator =
      new GitReferenceValidator(project, myGitRoot, getBranchToCheckoutTextField(), myValidateButton, new Runnable() {
        public void run() {
          checkOkButton();
        }
      });
    setupNewBranchName();
    init();
    checkOkButton();
  }

  /**
   * Validate if ok button should be enabled and set appropriate error
   */
  private void checkOkButton() {
    final String sourceRev = getSourceBranch();
    if (sourceRev == null || sourceRev.length() == 0) {
      setErrorText(null);
      setOKActionEnabled(false);
      return;
    }
    if (myBranchToCkeckoutValidator.isInvalid()) {
      setErrorText(GitBundle.getString("checkout.validation.failed"));
      setOKActionEnabled(false);
      return;
    }
    final String newBranchName = myNewBranchName.getText();
    if (newBranchName.length() != 0 && !GitBranchNameValidator.INSTANCE.checkInput(newBranchName)) {
      setErrorText(GitBundle.getString("checkout.invalid.new.branch.name"));
      setOKActionEnabled(false);
      return;
    }
    if (existingBranches.contains(newBranchName) && !myOverrideCheckBox.isSelected()) {
      setErrorText(GitBundle.getString("checkout.branch.name.exists"));
      setOKActionEnabled(false);
      return;
    }
    setErrorText(null);
    setOKActionEnabled(true);
  }

  /**
   * Setup {@link #myNewBranchName}
   */
  private void setupNewBranchName() {
    myOverrideCheckBox.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        checkOkButton();
      }
    });
    final DocumentAdapter l = new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        checkOkButton();
        final String text = myNewBranchName.getText();
        if (text.length() == 0) {
          disableCheckboxes();
        }
        else {
          if (GitBranchNameValidator.INSTANCE.checkInput(text)) {
            if (existingBranches.contains(text)) {
              myOverrideCheckBox.setEnabled(true);
            }
            else {
              myOverrideCheckBox.setEnabled(false);
              myOverrideCheckBox.setSelected(false);
            }
            if (existingBranches.contains(getSourceBranch())) {
              if (!myTrackBranchCheckBox.isEnabled()) {
                myTrackBranchCheckBox.setSelected(true);
                myTrackBranchCheckBox.setEnabled(true);
              }
            }
            else {
              myTrackBranchCheckBox.setSelected(false);
              myTrackBranchCheckBox.setEnabled(false);
            }
            myCreateRefLogCheckBox.setEnabled(true);
          }
          else {
            disableCheckboxes();
          }
        }
      }

      private void disableCheckboxes() {
        myOverrideCheckBox.setSelected(false);
        myOverrideCheckBox.setEnabled(false);
        myTrackBranchCheckBox.setSelected(false);
        myTrackBranchCheckBox.setEnabled(false);
        myCreateRefLogCheckBox.setSelected(false);
        myCreateRefLogCheckBox.setEnabled(false);
      }
    };
    myNewBranchName.getDocument().addDocumentListener(l);
    final JTextField text = getBranchToCheckoutTextField();
    text.getDocument().addDocumentListener(l);
  }

  /**
   * @return text field for branch to checkout
   */
  private JTextField getBranchToCheckoutTextField() {
    return (JTextField)myBranchToCkeckout.getEditor().getEditorComponent();
  }

  /**
   * @return the branch, tag, or expression to checkout
   */
  public String getSourceBranch() {
    return GitUIUtil.getTextField(myBranchToCkeckout).getText();
  }

  /**
   * Setup {@link #myBranchToCkeckout}
   */
  private void setupBranches() {
    ActionListener l = new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        try {
          List<String> branchesAndTags = new ArrayList<String>();
          // get branches
          GitBranch.listAsStrings(myProject, gitRoot(), true, true, branchesAndTags);
          existingBranches.clear();
          existingBranches.addAll(branchesAndTags);
          Collections.sort(branchesAndTags);
          // get tags
          if (myIncludeTagsCheckBox.isSelected()) {
            int mark = branchesAndTags.size();
            GitTag.listAsStrings(myProject, gitRoot(), branchesAndTags);
            Collections.sort(branchesAndTags.subList(mark, branchesAndTags.size()));
          }
          myBranchToCkeckout.removeAllItems();
          for (String item : branchesAndTags) {
            myBranchToCkeckout.addItem(item);
          }
          myBranchToCkeckout.setSelectedItem("");
        }
        catch (VcsException ex) {
          GitVcs.getInstance(myProject)
            .showErrors(Collections.singletonList(ex), GitBundle.getString("checkout.retrieving.branches.and.tags"));
        }
      }
    };
    myGitRoot.addActionListener(l);
    l.actionPerformed(null);
    myIncludeTagsCheckBox.addActionListener(l);
  }

  /**
   * @return a handler that creates branch or null if branch creation is not needed.
   */
  @Nullable
  public GitSimpleHandler createBranchHandler() {
    final String branch = myNewBranchName.getText();
    if (branch.length() == 0) {
      return null;
    }
    GitSimpleHandler h = new GitSimpleHandler(myProject, gitRoot(), GitCommand.BRANCH);
    h.setNoSSH(true);
    if (myTrackBranchCheckBox.isSelected()) {
      h.addParameters("--track");
    }
    if (myCreateRefLogCheckBox.isSelected()) {
      h.addParameters("-l");
    }
    if (myOverrideCheckBox.isSelected()) {
      h.addParameters("-f");
    }
    h.addParameters(branch, getSourceBranch());
    return h;
  }

  /**
   * @return a handler that checkouts branch
   */
  public GitLineHandler checkoutHandler() {
    GitLineHandler h = new GitLineHandler(myProject, gitRoot(), GitCommand.CHECKOUT);
    h.setNoSSH(true);
    final String newBranch = myNewBranchName.getText();
    if (newBranch.length() == 0) {
      h.addParameters(getSourceBranch());
    }
    else {
      h.addParameters(newBranch);
    }
    return h;
  }


  /**
   * @return a currently selected git root
   */
  public VirtualFile gitRoot() {
    return (VirtualFile)myGitRoot.getSelectedItem();
  }

  /**
   * Setup {@link #myIncludeTagsCheckBox}
   */
  private void setupIncludeTags() {
    boolean tagsIncluded = mySettings.isCheckoutIncludesTags();
    myIncludeTagsCheckBox.setSelected(tagsIncluded);
    myIncludeTagsCheckBox.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        mySettings.setCheckoutIncludesTags(myIncludeTagsCheckBox.isSelected());
      }
    });
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
    return "reference.VersionControl.Git.CheckoutBranch";
  }
}
