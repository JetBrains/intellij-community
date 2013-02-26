/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.github.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.github.GithubSettings;

import javax.swing.*;

/**
 * @author oleg
 * @date 9/27/11
 */
public class GitHubCreateGistDialog extends DialogWrapper {
  private final GitHubCreateGistPanel myGithubCreateGistPanel;

  public GitHubCreateGistDialog(@NotNull final Project project) {
    super(project, true);
    myGithubCreateGistPanel = new GitHubCreateGistPanel();
    // Use saved settings for controls
    final GithubSettings settings = GithubSettings.getInstance();
    myGithubCreateGistPanel.setAnonymous(settings.isAnonymous());
    myGithubCreateGistPanel.setPrivate(settings.isPrivateGist());
    myGithubCreateGistPanel.setOpenInBrowser(settings.isOpenInBrowserGist());
    setTitle("Create Gist");
    init();
  }

  @NotNull
  protected Action[] createActions() {
    return new Action[] {getOKAction(), getCancelAction(), getHelpAction()};
  }

  @Override
  protected JComponent createCenterPanel() {
    return myGithubCreateGistPanel.getPanel();
  }

  @Override
  protected String getHelpId() {
    return "create.gist.dialog";
  }

  @Override
  protected void doOKAction() {
    // Store settings
    final GithubSettings settings = GithubSettings.getInstance();
    settings.setAnonymousGist(myGithubCreateGistPanel.isAnonymous());
    settings.setOpenInBrowserGist(myGithubCreateGistPanel.isOpenInBrowser());
    settings.setPrivateGist(myGithubCreateGistPanel.isPrivate());
    super.doOKAction();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myGithubCreateGistPanel.getDescriptionTextArea();
  }

  public boolean isPrivate() {
    return myGithubCreateGistPanel.isPrivate();
  }

  public boolean isAnonymous() {
    return myGithubCreateGistPanel.isAnonymous();
  }

  public String getDescription() {
    return myGithubCreateGistPanel.getDescriptionTextArea().getText();
  }

  public boolean isOpenInBrowser() {
    return myGithubCreateGistPanel.isOpenInBrowser();
  }
}
