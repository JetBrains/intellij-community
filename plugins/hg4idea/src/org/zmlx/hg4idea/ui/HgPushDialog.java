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
import org.zmlx.hg4idea.command.HgShowConfigCommand;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;

public class HgPushDialog extends DialogWrapper {

  private final Project project;

  private JPanel contentPanel;
  private JTextField repositoryTxt;
  private JCheckBox revisionCbx;
  private JTextField revisionTxt;
  private HgRepositorySelectorComponent hgRepositorySelectorComponent;

  public HgPushDialog(Project project) {
    super(project, false);
    this.project = project;
    hgRepositorySelectorComponent.setTitle("Select repository to push from");
    hgRepositorySelectorComponent.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updateRepository();
      }
    });

    revisionCbx.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        update();
      }
    });

    DocumentListener documentListener = new DocumentListener() {
      public void insertUpdate(DocumentEvent e) {
        update();
      }

      public void removeUpdate(DocumentEvent e) {
        update();
      }

      public void changedUpdate(DocumentEvent e) {
        update();
      }
    };

    repositoryTxt.getDocument().addDocumentListener(documentListener);
    revisionTxt.getDocument().addDocumentListener(documentListener);

    setTitle("Push");
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

  public boolean isRevisionSelected() {
    return revisionCbx.isSelected();
  }

  public String getRevision() {
    return revisionTxt.getText();
  }

  private void updateRepository() {
    VirtualFile repo = hgRepositorySelectorComponent.getRepository();
    HgShowConfigCommand configCommand = new HgShowConfigCommand(project);
    String defaultPath = configCommand.getDefaultPath(repo);
    repositoryTxt.setText(defaultPath);
    update();
  }

  protected JComponent createCenterPanel() {
    return contentPanel;
  }

  private void update() {
    setOKActionEnabled(validateOptions());
    revisionTxt.setEnabled(revisionCbx.isSelected());
  }

  private boolean validateOptions() {
    return StringUtils.isNotBlank(repositoryTxt.getText())
      && !(revisionCbx.isSelected() && StringUtils.isBlank(revisionTxt.getText()));
  }

}
