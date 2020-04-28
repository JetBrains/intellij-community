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

import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorComboBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgBundle;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.util.HgUtil;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;

public class HgPullDialog extends DialogWrapper {

  private final Project project;
  private HgRepositorySelectorComponent hgRepositorySelector;
  private JPanel mainPanel;
  private EditorComboBox myRepositoryURL;
  private String myCurrentRepositoryUrl;

  public HgPullDialog(@NotNull Project project, @NotNull Collection<HgRepository> repositories, @Nullable final HgRepository selectedRepo) {
    super(project, false);
    this.project = project;
    hgRepositorySelector.setTitle(HgBundle.message("action.hg4idea.pull.select.repo"));
    hgRepositorySelector.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        onChangeRepository();
      }
    });

    setTitle(HgBundle.message("action.hg4idea.pull"));
    setOKButtonText(HgBundle.message("action.hg4idea.pull"));
    init();
    setRoots(repositories, selectedRepo);
  }

  public void createUIComponents() {
    myRepositoryURL = new EditorComboBox("");
    myRepositoryURL.addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(@NotNull DocumentEvent e) {
        onChangePullSource();
      }
    });
  }

  private void addPathsFromHgrc(@NotNull VirtualFile repo) {
    Collection<String> paths = HgUtil.getRepositoryPaths(project, repo);
    for (String path : paths) {
      myRepositoryURL.prependItem(path);
    }
  }

  @NotNull
  public HgRepository getRepository() {
    return hgRepositorySelector.getRepository();
  }

  public String getSource() {
    return myCurrentRepositoryUrl;
  }

  private void setRoots(@NotNull Collection<HgRepository> repositories, @Nullable final HgRepository selectedRepo) {
    hgRepositorySelector.setRoots(repositories);
    hgRepositorySelector.setSelectedRoot(selectedRepo);
    onChangeRepository();
  }

  @Override
  protected JComponent createCenterPanel() {
    return mainPanel;
  }

  @Override
  protected String getHelpId() {
    return "reference.mercurial.pull.dialog";
  }

  private void onChangeRepository() {
    final VirtualFile repo = hgRepositorySelector.getRepository().getRoot();
    final String defaultPath = HgUtil.getRepositoryDefaultPath(project, repo);
    if (!StringUtil.isEmptyOrSpaces(defaultPath)) {
      addPathsFromHgrc(repo);
      myRepositoryURL.setText(HgUtil.removePasswordIfNeeded(defaultPath));
      myCurrentRepositoryUrl = defaultPath;
      onChangePullSource();
    }
  }

  private void onChangePullSource() {
    myCurrentRepositoryUrl = myRepositoryURL.getText();
    setOKActionEnabled(!StringUtil.isEmptyOrSpaces(myRepositoryURL.getText()));
  }

  @Override
  protected String getDimensionServiceKey() {
    return HgPullDialog.class.getName();
  }
}
