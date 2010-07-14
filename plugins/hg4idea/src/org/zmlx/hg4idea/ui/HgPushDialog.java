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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgVcsMessages;
import org.zmlx.hg4idea.command.HgShowConfigCommand;
import org.zmlx.hg4idea.command.HgTagBranch;
import org.zmlx.hg4idea.command.HgTagBranchCommand;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;
import java.util.List;

public class HgPushDialog extends DialogWrapper {

  private final Project myProject;

  private JPanel contentPanel;
  private JTextField repositoryTxt;
  private JCheckBox revisionCbx;
  private JTextField revisionTxt;
  private HgRepositorySelectorComponent hgRepositorySelectorComponent;
  private JCheckBox forceCheckBox;
  private JCheckBox branchCheckBox;
  private JComboBox branchComboBox;

  public HgPushDialog(Project project) {
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
    repositoryTxt.getDocument().addDocumentListener(updatingListener);
    revisionTxt.getDocument().addDocumentListener(updatingListener);

    setTitle(HgVcsMessages.message("hg4idea.push.dialog.title"));
    init();
  }

  public void setRoots(Collection<VirtualFile> repos) {
    hgRepositorySelectorComponent.setRoots(repos);
    updateRepository();
  }

  public VirtualFile getRepository() {
    return hgRepositorySelectorComponent.getRepository();
  }

  public String getTarget() {
    return repositoryTxt.getText();
  }

  @Nullable
  public String getRevision() {
    return revisionCbx.isSelected() ? revisionTxt.getText() : null;
  }

  @Nullable
  public HgTagBranch getBranch() {
    return branchCheckBox.isSelected() ? (HgTagBranch) branchComboBox.getSelectedItem() : null;
  }

  public boolean isForce() {
    return forceCheckBox.isSelected();
  }

  protected JComponent createCenterPanel() {
    return contentPanel;
  }

  private void updateRepository() {
    final VirtualFile repo = hgRepositorySelectorComponent.getRepository();
    final HgShowConfigCommand configCommand = new HgShowConfigCommand(myProject);
    final String defaultPath = configCommand.getDefaultPath(repo);
    repositoryTxt.setText(defaultPath);
    loadBranches(repo);
    update();
  }

  private void loadBranches(VirtualFile root) {
    final List<HgTagBranch> branches = new HgTagBranchCommand(myProject, root).listBranches();
    branchComboBox.setModel(new DefaultComboBoxModel(branches.toArray()));
  }

  private void update() {
    setOKActionEnabled(validateOptions());
    revisionTxt.setEnabled(revisionCbx.isSelected());
    branchComboBox.setEnabled(branchCheckBox.isSelected());
  }

  private boolean validateOptions() {
    return StringUtils.isNotBlank(repositoryTxt.getText())
      && !(revisionCbx.isSelected() && StringUtils.isBlank(revisionTxt.getText()))
      && !(branchCheckBox.isSelected() && (branchComboBox.getSelectedItem() == null));
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
