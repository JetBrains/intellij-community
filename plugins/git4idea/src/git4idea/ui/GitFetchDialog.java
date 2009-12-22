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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import git4idea.commands.GitCommand;
import git4idea.commands.GitLineHandler;
import git4idea.i18n.GitBundle;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

/**
 * Fetch dialog. It represents most of the parameters for "git fetch" operation.
 */
public class GitFetchDialog extends DialogWrapper {
  /**
   * The git root
   */
  private JComboBox myGitRoot;
  /**
   * Reference specification panel
   */
  private GitRefspecPanel myRefspecs;
  /**
   * Fetch tags policy
   */
  private JComboBox myFetchTagsComboBox;
  /**
   * Force reference updates
   */
  private JCheckBox myForceReferencesUpdateCheckBox;
  /**
   * Remote name/url
   */
  private JComboBox myRemote;
  /**
   * The root panel
   */
  private JPanel myPanel;
  /**
   * The project for the dialog
   */
  private final Project myProject;
  /**
   * Fetch tags for fetched commits (default)
   */
  private static final String TAGS_POLICY_FOR_FETCHED_COMMITS = GitBundle.getString("fetch.tags.policy.for.fetched.commits");
  /**
   * Fetch all tags policy
   */
  private static final String TAGS_POLICY_ALL = GitBundle.getString("fetch.tags.policy.all");
  /**
   * Fetch no tags except explicitly listed
   */
  private static final String TAGS_POLICY_NONE = GitBundle.getString("fetch.tags.policy.none");

  /**
   * A constructor
   *
   * @param project     the project
   * @param roots       the list of the roots
   * @param defaultRoot the default root to select
   */
  public GitFetchDialog(final Project project, final List<VirtualFile> roots, final VirtualFile defaultRoot) {
    super(project, true);
    setTitle(GitBundle.getString("fetch.title"));
    setOKButtonText(GitBundle.getString("fetch.button"));
    GitUIUtil.setupRootChooser(project, roots, defaultRoot, myGitRoot, null);
    myProject = project;
    myRefspecs.setProject(project);
    myRefspecs.setReferenceSource(GitRefspecPanel.ReferenceSource.FETCH);
    setupRemotes();
    setupFetchTagsPolicy();
    init();
    setupValidation();
  }


  /**
   * Setup validation for combobox
   */
  private void setupValidation() {
    final JTextField remoteTextField = getRemoteTextField();
    final DocumentAdapter listener = new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        if (remoteTextField.getText().length() == 0) {
          setOKActionEnabled(false);
          setErrorText(null);
          return;
        }
        final String result = myRefspecs.validateFields();
        if (result != null) {
          setOKActionEnabled(false);
          setErrorText(result.length() == 0 ? null : result);
          return;
        }
        setOKActionEnabled(true);
        setErrorText(null);
      }
    };
    remoteTextField.getDocument().addDocumentListener(listener);
    myRefspecs.addValidationRequiredListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        listener.changedUpdate(null);
      }
    });
    listener.changedUpdate(null);
  }

  /**
   * Setup fetch tags policy combobox
   */
  private void setupFetchTagsPolicy() {
    myFetchTagsComboBox.addItem(TAGS_POLICY_FOR_FETCHED_COMMITS);
    myFetchTagsComboBox.addItem(TAGS_POLICY_ALL);
    myFetchTagsComboBox.addItem(TAGS_POLICY_NONE);
    myFetchTagsComboBox.setSelectedIndex(0);
  }


  /**
   * Setup drop down with remotes
   */
  private void setupRemotes() {
    final ActionListener actionListener = new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        myRefspecs.setGitRoot(getGitRoot());
        updateRemotes();
      }
    };
    myGitRoot.addActionListener(actionListener);
    actionListener.actionPerformed(null);
    final JTextField textField = getRemoteTextField();
    final DocumentAdapter remoteListener = new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        myRefspecs.setRemote(textField.getText());
      }
    };
    textField.getDocument().addDocumentListener(remoteListener);
    remoteListener.changedUpdate(null);
  }

  /**
   * @return text field for {@link #myRemote}
   */
  private JTextField getRemoteTextField() {
    return (JTextField)myRemote.getEditor().getEditorComponent();
  }

  /**
   * Update remotes
   */
  private void updateRemotes() {
    GitUIUtil.setupRemotes(myProject, getGitRoot(), myRemote, true);
  }

  /**
   * @return get current git root
   */
  protected VirtualFile getGitRoot() {
    return (VirtualFile)myGitRoot.getSelectedItem();
  }

  /**
   * {@inheritDoc}
   */
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  /**
   * @return the handler for the fetch operation
   */
  public GitLineHandler fetchHandler() {
    GitLineHandler h = new GitLineHandler(myProject, getGitRoot(), GitCommand.FETCH);
    h.addParameters("-v");
    if (myForceReferencesUpdateCheckBox.isSelected()) {
      h.addParameters("--force");
    }
    String tagsPolicy = (String)myFetchTagsComboBox.getSelectedItem();
    if (TAGS_POLICY_ALL.equals(tagsPolicy)) {
      h.addParameters("--tags");
    }
    else if (TAGS_POLICY_NONE.equals(tagsPolicy)) {
      h.addParameters("--no-tags");
    }
    h.addParameters(getRemote());
    h.addParameters(myRefspecs.getReferences());
    return h;
  }

  /**
   * @return remote name or URL
   */
  public String getRemote() {
    return getRemoteTextField().getText();
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
    return "reference.VersionControl.Git.Fetch";
  }
}
