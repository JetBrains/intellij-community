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
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.plugins.github.util.GithubSettings;

import javax.swing.*;
import java.util.Collection;
import java.util.regex.Pattern;

/**
 * @author Aleksey Pivovarov
 */
public class GithubCreatePullRequestDialog extends DialogWrapper {
  private final GithubCreatePullRequestPanel myGithubCreatePullRequestPanel;
  private static final Pattern GITHUB_REPO_PATTERN = Pattern.compile("[a-zA-Z0-9_.-]+:[a-zA-Z0-9_.-]+");

  public GithubCreatePullRequestDialog(@NotNull final Project project,
                                       @NotNull Collection<String> branches,
                                       @Nullable String suggestedBranch,
                                       @NotNull Consumer<String> showDiff) {
    super(project, true);
    myGithubCreatePullRequestPanel = new GithubCreatePullRequestPanel(showDiff);

    myGithubCreatePullRequestPanel.setBranches(branches);

    String configBranch = GithubSettings.getInstance().getCreatePullRequestDefaultBranch();
    myGithubCreatePullRequestPanel.setSelectedBranch(configBranch != null ? configBranch : suggestedBranch);

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

  @Override
  protected String getHelpId() {
    return "github.create.pull.request.dialog";
  }

  @Override
  protected String getDimensionServiceKey() {
    return "Github.CreatePullRequestDialog";
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

  @Nullable
  @Override
  protected ValidationInfo doValidate() {
    if (StringUtil.isEmptyOrSpaces(getRequestTitle())) {
      return new ValidationInfo("Title can't be empty'", myGithubCreatePullRequestPanel.getTitleTextField());
    }

    if (!GITHUB_REPO_PATTERN.matcher(getTargetBranch()).matches()) {
      return new ValidationInfo("Branch must be specified like 'username:branch'", myGithubCreatePullRequestPanel.getBranchEditor());
    }

    return null;
  }

  @TestOnly
  public void setRequestTitle(String title) {
    myGithubCreatePullRequestPanel.setTitle(title);
  }

  @TestOnly
  public void setBranch(String branch) {
    myGithubCreatePullRequestPanel.setSelectedBranch(branch);
  }
}
