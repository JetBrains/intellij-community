/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.plugins.github.GithubSettings;

import javax.swing.*;
import java.util.Collection;
import java.util.regex.Pattern;

/**
 * @author Aleksey Pivovarov
 */
public class GithubCreatePullRequestDialog extends DialogWrapper {
  private final GithubCreatePullRequestPanel myGithubCreatePullRequestPanel;
  private static final Pattern GITHUB_REPO_PATTERN = Pattern.compile("[a-zA-Z0-9_.-]+:[a-zA-Z0-9_.-]+");

  public GithubCreatePullRequestDialog(@NotNull final Project project) {
    super(project, true);
    myGithubCreatePullRequestPanel = new GithubCreatePullRequestPanel();

    myGithubCreatePullRequestPanel.setBranch(GithubSettings.getInstance().getCreatePullRequestDefaultBranch());

    setTitle("Create Pull Request");
    init();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myGithubCreatePullRequestPanel.getPanel();
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myGithubCreatePullRequestPanel.getPreferredComponent();
  }

  @NotNull
  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction()};
  }

  @NotNull
  public String getRequestTitle() {
    return myGithubCreatePullRequestPanel.getTitle();
  }

  @NotNull
  public String getDescription() {
    return myGithubCreatePullRequestPanel.getDescription();
  }

  @NotNull
  public String getTargetBranch() {
    return myGithubCreatePullRequestPanel.getBranch();
  }

  @Override
  protected void doOKAction() {
    super.doOKAction();
    GithubSettings.getInstance().setCreatePullRequestDefaultBranch(getTargetBranch());
  }

  public void addBranches(@NotNull Collection<String> branches) {
    myGithubCreatePullRequestPanel.addBranches(branches);
  }

  public void setBusy(boolean busy) {
    myGithubCreatePullRequestPanel.setBusy(busy);
  }

  @Nullable
  @Override
  protected ValidationInfo doValidate() {
    if (StringUtil.isEmptyOrSpaces(getRequestTitle())) {
      return new ValidationInfo("Title can't be empty'", myGithubCreatePullRequestPanel.getTitleTextField());
    }

    if (!GITHUB_REPO_PATTERN.matcher(getTargetBranch()).matches()) {
      return new ValidationInfo("Branch must be specified like 'username:branch'", myGithubCreatePullRequestPanel.getComboBox());
    }

    return null;
  }

  public void setRequestTitle(String title) {
    myGithubCreatePullRequestPanel.setTitle(title);
  }

  @TestOnly
  public void setBranch(String branch) {
    myGithubCreatePullRequestPanel.setBranch(branch);
  }
}
