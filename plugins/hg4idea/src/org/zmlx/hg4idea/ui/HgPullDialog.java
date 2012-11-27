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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorComboBox;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.UIUtil;
import org.zmlx.hg4idea.HgRememberedInputs;
import org.zmlx.hg4idea.command.HgShowConfigCommand;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;

public class HgPullDialog extends DialogWrapper {

  private final Project project;
  private HgRepositorySelectorComponent hgRepositorySelector;
  private JPanel mainPanel;
  private EditorComboBox myRepositoryURL;

  public HgPullDialog(Project project) {
    super(project, false);
    this.project = project;
    hgRepositorySelector.setTitle("Select repository to pull changesets for");
    hgRepositorySelector.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        onChangeRepository();
      }
    });

    setTitle("Pull");
    setOKButtonText("Pull");
    init();
  }

  public void createUIComponents() {
    myRepositoryURL = new EditorComboBox("");
    final HgRememberedInputs rememberedInputs = HgRememberedInputs.getInstance(project);
    myRepositoryURL.setHistory(ArrayUtil.toObjectArray(rememberedInputs.getRepositoryUrls(), String.class));
    myRepositoryURL.addDocumentListener(new DocumentAdapter() {
      @Override
      public void documentChanged(DocumentEvent e) {
        onChangePullSource();
      }
    });
  }

  public void rememberSettings() {
    final HgRememberedInputs rememberedInputs = HgRememberedInputs.getInstance(project);
    rememberedInputs.addRepositoryUrl(getSource());
  }


  public VirtualFile getRepository() {
    return hgRepositorySelector.getRepository();
  }

  public String getSource() {
    return myRepositoryURL.getText();
  }

  public void setRoots(Collection<VirtualFile> repos) {
    hgRepositorySelector.setRoots(repos);
    onChangeRepository();
  }

  protected JComponent createCenterPanel() {
    return mainPanel;
  }

  @Override
  protected String getHelpId() {
    return "reference.mercurial.pull.dialog";
  }

  private void onChangeRepository() {
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        VirtualFile repo = hgRepositorySelector.getRepository();
        HgShowConfigCommand configCommand = new HgShowConfigCommand(project);
        final String defaultPath = configCommand.getDefaultPath(repo);
        UIUtil.invokeAndWaitIfNeeded(new Runnable() {
          @Override
          public void run() {
            myRepositoryURL.setText(defaultPath);
          }
        });

        onChangePullSource();
      }
    });
  }

  private void onChangePullSource() {
    setOKActionEnabled(!StringUtil.isEmptyOrSpaces(myRepositoryURL.getText()));
  }

  @Override
  protected String getDimensionServiceKey() {
    return HgPullDialog.class.getName();
  }

}
