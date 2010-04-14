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
package git4idea.checkin;

import com.intellij.ide.util.ElementsChooser;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.containers.HashMap;
import git4idea.GitBranch;
import git4idea.GitRemote;
import git4idea.GitTag;
import git4idea.commands.GitCommand;
import git4idea.commands.GitLineHandler;
import git4idea.config.GitConfigUtil;
import git4idea.i18n.GitBundle;
import git4idea.ui.GitUIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

/**
 * The push dialog
 */
public class GitPushDialog extends DialogWrapper {
  /**
   * the logger
   */
  private static final Logger LOG = Logger.getInstance(GitPushDialog.class.getName());
  /**
   * Push policy meaning selected references
   */
  private static final String PUSH_POLICY_SELECTED = GitBundle.getString("push.policy.selected");
  /**
   * This push policy means that simple "git push" will be used, so push will happen according to the push configuration.
   */
  private static final String PUSH_POLICY_DEFAULT = GitBundle.getString("push.policy.default");
  /**
   * Push policy meaning all references
   */
  private static final String PUSH_POLICY_ALL = GitBundle.getString("push.policy.all");
  /**
   * Push policy meaning mirror
   */
  private static final String PUSH_POLICY_MIRROR = GitBundle.getString("push.policy.mirror");
  /**
   * The root panel
   */
  private JPanel myPanel;
  /**
   * Git root selector
   */
  private JComboBox myGitRootComboBox;
  /**
   * Remote name combobox
   */
  private JComboBox myRemoteComboBox;
  /**
   * Push tags flag
   */
  private JCheckBox myPushTagsCheckBox;
  /**
   * Use thin pack flag
   */
  private JCheckBox myUseThinPackCheckBox;
  /**
   * The push policy drop down
   */
  private JComboBox myPushPolicy;
  /**
   * Force update checkbox
   */
  private JCheckBox myForceUpdateCheckBox;
  /**
   * Chooser for branches
   */
  private ElementsChooser<String> myBranchChooser;
  /**
   * The current branch label
   */
  private JLabel myCurrentBranch;
  /**
   * The checkbox that specifies whether tags are shown
   */
  private JCheckBox myShowTagsCheckBox;
  /**
   * The list of branches
   */
  private final ArrayList<String> myBranchNames = new ArrayList<String>();
  /**
   * The list of branches
   */
  private final ArrayList<String> myTagNames = new ArrayList<String>();
  /**
   * The map from list of branches to the result of mirror checks
   */
  private final HashMap<String, Boolean> myMirrorChecks = new HashMap<String, Boolean>();
  /**
   * The current project
   */
  private final Project myProject;

  /**
   * A constructor
   *
   * @param project     the project
   * @param roots       the list of the roots
   * @param defaultRoot the default root to select
   */
  public GitPushDialog(final Project project, final List<VirtualFile> roots, final VirtualFile defaultRoot) {
    super(project, true);
    setTitle(GitBundle.getString("push.title"));
    setOKButtonText(GitBundle.getString("push.button"));
    GitUIUtil.setupRootChooser(project, roots, defaultRoot, myGitRootComboBox, myCurrentBranch);
    myProject = project;
    setupRemotes();
    setupPolicy();
    setupValidation();
    myShowTagsCheckBox.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        if (myPushPolicy.getSelectedItem().equals(PUSH_POLICY_SELECTED)) {
          updateBranchChooser();
        }
      }
    });
    init();
  }

  /**
   * @return a prepared handler for push operation
   */
  public GitLineHandler handler() {
    GitLineHandler h = new GitLineHandler(myProject, getGitRoot(), GitCommand.PUSH);
    String policy = (String)myPushPolicy.getSelectedItem();
    if (PUSH_POLICY_ALL.equals(policy)) {
      h.addParameters("--all");
    }
    else if (PUSH_POLICY_MIRROR.equals(policy)) {
      h.addParameters("--mirror");
    }
    if (myPushTagsCheckBox.isEnabled() && myPushTagsCheckBox.isSelected()) {
      h.addParameters("--tags");
    }
    if (myUseThinPackCheckBox.isSelected()) {
      h.addParameters("--thin");
    }
    if (myForceUpdateCheckBox.isSelected()) {
      h.addParameters("--force");
    }
    h.addParameters("-v");
    h.addParameters(getRemoteTextField().getText());
    if (PUSH_POLICY_SELECTED.equals(policy)) {
      for (String b : myBranchChooser.getMarkedElements()) {
        if (myBranchNames.contains(b)) {
          h.addParameters(b);
        }
        else {
          h.addParameters("tag", b);
        }
      }
    }
    return h;
  }

  /**
   * Setup dialog validation
   */
  private void setupValidation() {
    myPushPolicy.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        validateFields();
      }
    });
    getRemoteTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        validateFields();
      }
    });
    myBranchChooser.addElementsMarkListener(new ElementsChooser.ElementsMarkListener<String>() {
      public void elementMarkChanged(final String element, final boolean isMarked) {
        validateFields();
      }
    });
    validateFields();
  }

  /**
   * Validate fields in the dialog
   */
  private void validateFields() {
    boolean isValid = getRemoteTextField().getText().length() != 0;
    final Object policy = myPushPolicy.getSelectedItem();
    isValid &= !policy.equals(PUSH_POLICY_SELECTED) || myBranchChooser.getMarkedElements().size() != 0;
    setOKActionEnabled(isValid);
  }

  /**
   * Setup policy combobox
   */
  private void setupPolicy() {
    myPushPolicy.addItem(PUSH_POLICY_SELECTED);
    myPushPolicy.addItem(PUSH_POLICY_DEFAULT);
    myPushPolicy.addItem(PUSH_POLICY_ALL);
    myPushPolicy.addItem(PUSH_POLICY_MIRROR);
    myPushPolicy.setSelectedIndex(0);
    // configure policy listener
    final ActionListener policyListener = new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        String p = (String)myPushPolicy.getSelectedItem();
        if (PUSH_POLICY_SELECTED.equals(p)) {
          myBranchChooser.setEnabled(true);
          myShowTagsCheckBox.setEnabled(true);
          updateBranchChooser();
        }
        else {
          myBranchChooser.clear();
          myBranchChooser.setEnabled(false);
          myShowTagsCheckBox.setEnabled(false);
        }
        if (PUSH_POLICY_MIRROR.equals(p)) {
          myPushTagsCheckBox.setEnabled(false);
          myPushTagsCheckBox.setSelected(true);
          myForceUpdateCheckBox.setEnabled(false);
          myForceUpdateCheckBox.setSelected(true);
        }
        else {
          if (!myForceUpdateCheckBox.isEnabled()) {
            myForceUpdateCheckBox.setEnabled(true);
            myForceUpdateCheckBox.setSelected(false);
          }
          if (PUSH_POLICY_ALL.equals(p)) {
            myPushTagsCheckBox.setEnabled(false);
            myPushTagsCheckBox.setSelected(false);
          }
          else if (!myPushTagsCheckBox.isEnabled()) {
            myPushTagsCheckBox.setEnabled(true);
            myPushTagsCheckBox.setSelected(false);
          }
        }
      }
    };
    myPushPolicy.addActionListener(policyListener);
    policyListener.actionPerformed(null);
    // select remote listener
    final DocumentAdapter listener = new DocumentAdapter() {
      VirtualFile myPreviousRoot;
      GitRemote myPreviousRemote = null;

      protected void textChanged(final DocumentEvent e) {
        final VirtualFile newRoot = getGitRoot();
        final GitRemote newRemote = getRemote(getRemoteTextField().getText());
        if (newRoot == null) {
          return;
        }
        if (myPreviousRoot == null || myPreviousRemote == null || !myPreviousRoot.equals(newRoot) || !myPreviousRemote.equals(newRemote)) {
          if (isMirror()) {
            myPushPolicy.setEnabled(false);
            myPushPolicy.setSelectedItem(PUSH_POLICY_MIRROR);
          }
          else {
            if (!myPushPolicy.isEnabled()) {
              myPushPolicy.setSelectedItem(PUSH_POLICY_SELECTED);
              myPushPolicy.setEnabled(true);
            }
          }
          myPreviousRoot = newRoot;
          myPreviousRemote = newRemote;
        }
      }
    };
    getRemoteTextField().getDocument().addDocumentListener(listener);
    myGitRootComboBox.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        listener.changedUpdate(null);
      }
    });
    listener.changedUpdate(null);
  }

  /**
   * Update content of the branch chooser
   */
  private void updateBranchChooser() {
    myBranchChooser.clear();
    final String current = myCurrentBranch.getText();
    for (String b : myBranchNames) {
      myBranchChooser.addElement(b, b.equals(current));
    }
    if (myShowTagsCheckBox.isSelected()) {
      for (String t : myTagNames) {
        myBranchChooser.addElement(t, false);
      }
    }
    validateFields();
  }

  /**
   * @return true if the current branch should be mirror branch
   */
  private boolean isMirror() {
    final String name = getRemoteTextField().getText();
    Boolean rc = myMirrorChecks.get(name);
    if (rc == null) {
      rc = false;
      GitRemote remote = getRemote(name);
      if (remote != null) {
        try {
          rc = GitConfigUtil.getBoolValue(myProject, getGitRoot(), "remote." + name + ".mirror");
          if (rc == null) {
            rc = false;
          }
        }
        catch (VcsException e) {
          // treat error as a false value
        }
        myMirrorChecks.put(name, rc);
      }
    }
    return rc.booleanValue();
  }

  /**
   * Get currently selected remote object.
   *
   * @param name a name to select
   * @return the remote or null
   */
  @Nullable
  private GitRemote getRemote(final String name) {
    for (int i = myRemoteComboBox.getItemCount() - 1; i >= 0; i--) {
      final GitRemote r = (GitRemote)myRemoteComboBox.getItemAt(i);
      if (name.equals(r.toString())) {
        return r;
      }
    }
    return null;
  }

  /**
   * Setup drop down with remotes
   */
  private void setupRemotes() {
    final ActionListener actionListener = new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        updateRemotes();
        myMirrorChecks.clear();
        myBranchNames.clear();
        myTagNames.clear();
        try {
          GitBranch.listAsStrings(myProject, getGitRoot(), false, true, myBranchNames, null);
          GitTag.listAsStrings(myProject, getGitRoot(), myTagNames, null);
        }
        catch (VcsException ex) {
          LOG.warn("Exception in branch list: \n" + StringUtil.getThrowableText(ex));
        }
      }
    };
    myGitRootComboBox.addActionListener(actionListener);
    actionListener.actionPerformed(null);
  }

  /**
   * Update remotes
   */
  private void updateRemotes() {
    GitUIUtil.setupRemotes(myProject, getGitRoot(), myRemoteComboBox, false);
  }

  /**
   * @return text field for {@link #myRemoteComboBox}
   */
  private JTextField getRemoteTextField() {
    return (JTextField)myRemoteComboBox.getEditor().getEditorComponent();
  }

  /**
   * @return the currently selected git root
   */
  private VirtualFile getGitRoot() {
    return (VirtualFile)myGitRootComboBox.getSelectedItem();
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
    return "reference.VersionControl.Git.Push";
  }

  /**
   * Create UI components
   */
  private void createUIComponents() {
    myBranchChooser = new ElementsChooser<String>(true);
  }
}
