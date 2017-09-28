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

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.util.GithubSettings;

import javax.swing.*;

/**
 * @author oleg
 * @date 9/27/11
 */
public class GithubCreateGistDialog extends DialogWrapper {
  private final GithubCreateGistPanel myGithubCreateGistPanel;

  public GithubCreateGistDialog(@NotNull final Project project, @Nullable Editor editor, @Nullable VirtualFile[] files, @Nullable VirtualFile file) {
    super(project, true);
    myGithubCreateGistPanel = new GithubCreateGistPanel();
    // Use saved settings for controls
    final GithubSettings settings = GithubSettings.getInstance();
    myGithubCreateGistPanel.setAnonymous(settings.isAnonymousGist());
    myGithubCreateGistPanel.setPrivate(settings.isPrivateGist());
    myGithubCreateGistPanel.setOpenInBrowser(settings.isOpenInBrowserGist());
    myGithubCreateGistPanel.setCopyURLToClipboard(settings.isCopyURLToClipboardGist());

    if (editor != null) {
      if (file != null) {
        myGithubCreateGistPanel.showFileNameField(file.getName());
      }
      else {
        myGithubCreateGistPanel.showFileNameField("");
      }
    }
    else if (files != null) {
      if (files.length == 1 && !files[0].isDirectory()) {
        myGithubCreateGistPanel.showFileNameField(files[0].getName());
      }
    }
    else if (file != null && !file.isDirectory()) {
      myGithubCreateGistPanel.showFileNameField(file.getName());
    }

    setTitle("Create Gist");
    init();
  }

  @Override
  protected JComponent createCenterPanel() {
    return myGithubCreateGistPanel.getPanel();
  }

  @Override
  protected String getHelpId() {
    return "github.create.gist.dialog";
  }

  @Override
  protected String getDimensionServiceKey() {
    return "Github.CreateGistDialog";
  }

  @Override
  protected void doOKAction() {
    // Store settings
    final GithubSettings settings = GithubSettings.getInstance();
    settings.setAnonymousGist(myGithubCreateGistPanel.isAnonymous());
    settings.setOpenInBrowserGist(myGithubCreateGistPanel.isOpenInBrowser());
    settings.setCopyURLToClipboardGist(myGithubCreateGistPanel.isCopyURLToClipboard());
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

  @NotNull
  public String getDescription() {
    return myGithubCreateGistPanel.getDescriptionTextArea().getText();
  }

  @Nullable
  public String getFileName() {
    return myGithubCreateGistPanel.getFileNameField().getText();
  }

  public boolean isOpenInBrowser() {
    return myGithubCreateGistPanel.isOpenInBrowser();
  }

  public boolean isCopyURLToClipboard() {
    return myGithubCreateGistPanel.isCopyURLToClipboard();
  }
}
