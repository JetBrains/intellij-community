package git4idea.config;
/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions and limitations under the License.
 *
 * Copyright 2007 Decentrix Inc
 * Copyright 2007 Aspiro AS
 * Copyright 2008 MQSoftware
 * Authors: gevession, Erlend Simonsen & Mark Scott
 *
 * This code was originally derived from the MKS & Mercurial IDEA VCS plugins
 */

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vcs.VcsException;
import git4idea.GitVcs;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Git VCS configuration panel
 */
public class GitVcsPanel {
  private JButton myTestButton;
  private JComponent myPanel;
  private TextFieldWithBrowseButton myGitField;
  private final Project myProject;
  private final GitVcsSettings mySettings;

  public GitVcsPanel(@NotNull Project project) {
    mySettings = GitVcsSettings.getInstance(project);
    this.myProject = project;
    myTestButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        testConnection();
      }
    });

    myGitField.addBrowseFolderListener(GitBundle.getString("find.git.title"), GitBundle.getString("find.git.description"), project,
                                       new FileChooserDescriptor(true, false, false, false, false, false));
  }

  private void testConnection() {
    mySettings.GIT_EXECUTABLE = myGitField.getText();
    final String s;
    try {
      s = GitVcs.version(myProject);
    }
    catch (VcsException e) {
      Messages.showErrorDialog(myProject, e.getMessage(), GitBundle.getString("find.git.error.title"));
      return;
    }
    Messages.showInfoMessage(myProject, s, GitBundle.getString("find.git.success.title"));
  }

  public JComponent getPanel() {
    return myPanel;
  }

  public void load(@NotNull GitVcsSettings settings) {
    myGitField.setText(settings.GIT_EXECUTABLE);
  }

  public boolean isModified(@NotNull GitVcsSettings settings) {
    return !settings.GIT_EXECUTABLE.equals(myGitField.getText());
  }

  public void save(@NotNull GitVcsSettings settings) {
    settings.GIT_EXECUTABLE = myGitField.getText();
  }
}
