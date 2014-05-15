// Copyright 2008-2010 Victor Iacoban
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
package org.zmlx.hg4idea.ui;

import com.intellij.dvcs.DvcsRememberedInputs;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ComboboxSpeedSearch;
import com.intellij.ui.EditorComboBox;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgRememberedInputs;
import org.zmlx.hg4idea.HgVcsMessages;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.util.HgUtil;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;

public class HgPushDialog extends DialogWrapper {

  private final Project myProject;

  private JPanel contentPanel;
  private JCheckBox revisionCbx;
  private JTextField revisionTxt;
  private HgRepositorySelectorComponent hgRepositorySelectorComponent;
  private JCheckBox forceCheckBox;
  private JCheckBox branchCheckBox;
  private JComboBox branchComboBox;
  private EditorComboBox myRepositoryURL;
  private JCheckBox newBranchCheckBox;
  private JComboBox myBookmarkComboBox;
  private JCheckBox myBookmarkCheckBox;
  private String myCurrentRepositoryUrl;

  public HgPushDialog(Project project, Collection<HgRepository> repos, @Nullable HgRepository selectedRepo) {
    super(project, false);
    myProject = project;

    hgRepositorySelectorComponent.setTitle("Select repository to push from");
    hgRepositorySelectorComponent.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updateRepository();
      }
    });

    final UpdatingListener updatingListener = new UpdatingListener();
    revisionCbx.addChangeListener(updatingListener);
    branchCheckBox.addChangeListener(updatingListener);
    myBookmarkCheckBox.addChangeListener(updatingListener);
    revisionTxt.getDocument().addDocumentListener(updatingListener);
    new ComboboxSpeedSearch(branchComboBox);
    new ComboboxSpeedSearch(myBookmarkComboBox);
    setTitle(HgVcsMessages.message("hg4idea.push.dialog.title"));
    setOKButtonText("Push");
    init();

    setRoots(repos, selectedRepo);
  }

  private void setRoots(@NotNull Collection<HgRepository> repos,
                        @Nullable HgRepository selectedRepo) {
    hgRepositorySelectorComponent.setRoots(repos);
    hgRepositorySelectorComponent.setSelectedRoot(selectedRepo);
    updateRepository();
  }

  public void createUIComponents() {
    myRepositoryURL = new EditorComboBox("");
    final DvcsRememberedInputs rememberedInputs = HgRememberedInputs.getInstance();
    myRepositoryURL.setHistory(ArrayUtil.toObjectArray(rememberedInputs.getVisitedUrls(), String.class));
    myRepositoryURL.addDocumentListener(new DocumentAdapter() {
      @Override
      public void documentChanged(com.intellij.openapi.editor.event.DocumentEvent e) {
        myCurrentRepositoryUrl = myRepositoryURL.getText();
        setOKActionEnabled(!StringUtil.isEmptyOrSpaces(myRepositoryURL.getText()));
      }
    });
  }

  private void addPathsFromHgrc(VirtualFile repo) {
    Collection<String> paths = HgUtil.getRepositoryPaths(myProject, repo);
    for (String path : paths) {
      myRepositoryURL.prependItem(path);
    }
  }

  @NotNull
  public HgRepository getRepository() {
    return hgRepositorySelectorComponent.getRepository();
  }

  @NotNull
  public String getTarget() {
    return myCurrentRepositoryUrl;
  }

  @Nullable
  public String getRevision() {
    return revisionCbx.isSelected() ? revisionTxt.getText() : null;
  }

  @Nullable
  public String getBranch() {
    return branchCheckBox.isSelected() ? (String)branchComboBox.getSelectedItem() : null;
  }

  @Nullable
  public String getBookmarkName() {
    return myBookmarkCheckBox.isSelected() ? (String)myBookmarkComboBox.getSelectedItem() : null;
  }

  public boolean isForce() {
    return forceCheckBox.isSelected();
  }

  public boolean isNewBranch() {
    return newBranchCheckBox.isSelected();
  }

  protected JComponent createCenterPanel() {
    return contentPanel;
  }

  @Override
  protected String getHelpId() {
    return "reference.mercurial.push.dialog";
  }

  public void updateRepository() {
    HgRepository repo = hgRepositorySelectorComponent.getRepository();
    String defaultPath = HgUtil.getRepositoryDefaultPushPath(repo);
    addPathsFromHgrc(repo.getRoot());
    if (defaultPath != null) {
      updateRepositoryUrlText(HgUtil.removePasswordIfNeeded(defaultPath));
      myCurrentRepositoryUrl = defaultPath;
    }
    updateComboBoxes(repo);
  }

  private void updateComboBoxes(HgRepository repo) {
    final Collection<String> branches = repo.getOpenedBranches();
    final Collection<String> bookmarkNames = HgUtil.getNamesWithoutHashes(repo.getBookmarks());
    branchComboBox.setModel(new DefaultComboBoxModel(branches.toArray()));
    branchComboBox.setSelectedItem(repo.getCurrentBranch());
    myBookmarkComboBox.setModel(new DefaultComboBoxModel(bookmarkNames.toArray()));
  }

  private void updateRepositoryUrlText(String defaultPath) {
    if (defaultPath != null) {
      myRepositoryURL.setSelectedItem(defaultPath);
      update();
    }
  }

  private void update() {
    setOKActionEnabled(validateOptions());
    revisionTxt.setEnabled(revisionCbx.isSelected());
    branchComboBox.setEnabled(branchCheckBox.isSelected());
    myBookmarkComboBox.setEnabled(myBookmarkCheckBox.isSelected());
  }

  private boolean validateOptions() {
    return !StringUtil.isEmptyOrSpaces(myCurrentRepositoryUrl)
      && !(revisionCbx.isSelected() && StringUtil.isEmptyOrSpaces(revisionTxt.getText()))
      && !(branchCheckBox.isSelected() && (branchComboBox.getSelectedItem() == null));
  }

  @Override
  protected String getDimensionServiceKey() {
    return HgPushDialog.class.getName();
  }

  public void rememberSettings() {
    final DvcsRememberedInputs rememberedInputs = HgRememberedInputs.getInstance();
    rememberedInputs.addUrl(HgUtil.removePasswordIfNeeded(myRepositoryURL.getText()));
  }

  /**
   * Updates the form on every change.
   */
  private class UpdatingListener implements ChangeListener, DocumentListener {

    public void stateChanged(ChangeEvent e) {
      update();
    }

    public void insertUpdate(DocumentEvent e) {
      update();
    }

    public void removeUpdate(DocumentEvent e) {
      update();
    }

    public void changedUpdate(DocumentEvent e) {
      update();
    }
  }
}
