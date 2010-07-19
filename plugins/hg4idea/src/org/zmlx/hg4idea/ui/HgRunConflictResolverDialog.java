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
import org.zmlx.hg4idea.HgFile;
import org.zmlx.hg4idea.command.HgResolveCommand;
import org.zmlx.hg4idea.command.HgResolveStatusEnum;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;
import java.util.Map;

public class HgRunConflictResolverDialog extends DialogWrapper {

  private JPanel mainPanel;
  private HgRepositorySelectorComponent repositorySelector;
  private JList conflictsList;

  private final Project project;

  public HgRunConflictResolverDialog(Project project) {
    super(project, false);
    this.project = project;
    repositorySelector.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        onChangeRepository();
      }
    });
    setTitle("Resolve Conflicts");
    init();
  }

  public VirtualFile getRepository() {
    return repositorySelector.getRepository();
  }

  public void setRoots(Collection<VirtualFile> repos) {
    repositorySelector.setRoots(repos);
    onChangeRepository();
  }

  protected JComponent createCenterPanel() {
    return mainPanel;
  }

  private void onChangeRepository() {
    VirtualFile repo = repositorySelector.getRepository();
    HgResolveCommand command = new HgResolveCommand(project);
    Map<HgFile, HgResolveStatusEnum> status = command.list(repo);
    DefaultListModel model = new DefaultListModel();
    for (Map.Entry<HgFile, HgResolveStatusEnum> entry : status.entrySet()) {
      if (entry.getValue() == HgResolveStatusEnum.UNRESOLVED) {
        model.addElement(entry.getKey().getRelativePath());
      }
    }
    setOKActionEnabled(!model.isEmpty());
    if (model.isEmpty()) {
      model.addElement("No conflicts to resolve");
    }
    conflictsList.setModel(model);
  }

}
