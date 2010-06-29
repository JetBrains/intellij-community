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

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.util.Collection;

public class HgTagDialog extends DialogWrapper {

  private JPanel contentPanel;
  private JTextField tagTxt;
  private HgRepositorySelectorComponent hgRepositorySelectorComponent;

  public HgTagDialog(Project project) {
    super(project, false);
    hgRepositorySelectorComponent.setTitle("Select repository to tag");
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

    tagTxt.getDocument().addDocumentListener(documentListener);

    setTitle("Tag");
    init();
  }

  public String getTagName() {
    return tagTxt.getText();
  }

  public VirtualFile getRepository() {
    return hgRepositorySelectorComponent.getRepository();
  }

  public void setRoots(Collection<VirtualFile> repos) {
    hgRepositorySelectorComponent.setRoots(repos);
    update();
  }

  protected JComponent createCenterPanel() {
    return contentPanel;
  }

  private void update() {
    setOKActionEnabled(validateOptions());
  }

  private boolean validateOptions() {
    return StringUtils.isNotBlank(tagTxt.getText());
  }

}
