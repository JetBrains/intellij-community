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
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import org.apache.commons.lang.StringUtils;
import org.zmlx.hg4idea.command.HgShowConfigCommand;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;

public class HgPullDialog extends DialogWrapper {

  private final Project project;
  private HgRepositorySelectorComponent hgRepositorySelector;
  private JTextField sourceTxt;
  private JPanel mainPanel;

  public HgPullDialog(Project project) {
    super(project, false);
    this.project = project;
    hgRepositorySelector.setTitle("Select repository to pull changesets for");
    hgRepositorySelector.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        onChangeRepository();
      }
    });
    DocumentListener documentListener = new DocumentListener() {
      public void insertUpdate(DocumentEvent e) {
        onChangePullSource();
      }

      public void removeUpdate(DocumentEvent e) {
        onChangePullSource();
      }

      public void changedUpdate(DocumentEvent e) {
        onChangePullSource();
      }
    };
    sourceTxt.getDocument().addDocumentListener(documentListener);
    setTitle("Pull");
    init();
  }

  public VirtualFile getRepository() {
    return hgRepositorySelector.getRepository();
  }

  public String getSource() {
    return sourceTxt.getText();
  }

  public void setRoots(Collection<VirtualFile> repos) {
    hgRepositorySelector.setRoots(repos);
    onChangeRepository();
  }

  protected JComponent createCenterPanel() {
    return mainPanel;
  }

  private void onChangeRepository() {
    VirtualFile repo = hgRepositorySelector.getRepository();
    HgShowConfigCommand configCommand = new HgShowConfigCommand(project);
    String defaultPath = configCommand.getDefaultPath(repo);
    sourceTxt.setText(defaultPath);
    onChangePullSource();
  }

  private void onChangePullSource() {
    setOKActionEnabled(StringUtils.isNotBlank(sourceTxt.getText()));
  }

}
