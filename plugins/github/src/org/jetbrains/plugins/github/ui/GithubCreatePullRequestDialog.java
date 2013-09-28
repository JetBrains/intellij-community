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
import git4idea.DialogManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.plugins.github.GithubCreatePullRequestWorker;
import org.jetbrains.plugins.github.api.GithubFullPath;
import org.jetbrains.plugins.github.util.GithubProjectSettings;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;

/**
 * @author Aleksey Pivovarov
 */
public class GithubCreatePullRequestDialog extends DialogWrapper {
  @NotNull private final GithubCreatePullRequestPanel myGithubCreatePullRequestPanel;
  @NotNull private final Project myProject;
  @NotNull GithubCreatePullRequestWorker myWorker;

  @Nullable private final GithubFullPath myInitForkPath;

  public GithubCreatePullRequestDialog(@NotNull GithubCreatePullRequestWorker worker, @Nullable GithubFullPath forkPath) {
    super(worker.getProject(), true);
    myWorker = worker;
    myProject = myWorker.getProject();
    myInitForkPath = forkPath;

    myGithubCreatePullRequestPanel = new GithubCreatePullRequestPanel(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myWorker.showDiffDialog(myGithubCreatePullRequestPanel.getBranch());
      }
    }, new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        showTargetDialog();
      }
    }
    );

    setTitle("Create Pull Request");
    init();
  }

  @Override
  public void show() {
    if (myInitForkPath != null) {
      setTarget(myInitForkPath);
    }
    else {
      showTargetDialog();
    }
    super.show();
  }

  private void showTargetDialog() {
    GithubFullPath forkPath = myWorker.showTargetDialog();
    if (forkPath == null) {
      doCancelAction();
      return;
    }
    setTarget(forkPath);
  }

  private void setTarget(@NotNull GithubFullPath forkPath) {
    Collection<String> branches = myWorker.setTarget(forkPath);
    updateBranches(branches, forkPath);
  }

  private void updateBranches(@Nullable Collection<String> branches, @NotNull GithubFullPath forkPath) {
    if (branches == null) {
      doCancelAction();
      return;
    }

    myGithubCreatePullRequestPanel.setBranches(branches);

    String configBranch = GithubProjectSettings.getInstance(myProject).getCreatePullRequestDefaultBranch();
    if (configBranch != null) myGithubCreatePullRequestPanel.setSelectedBranch(configBranch);

    myGithubCreatePullRequestPanel.setForkName(forkPath.getFullName());
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
  private String getRequestTitle() {
    return myGithubCreatePullRequestPanel.getTitle();
  }

  @NotNull
  private String getDescription() {
    return myGithubCreatePullRequestPanel.getDescription();
  }

  @NotNull
  private String getTargetBranch() {
    return myGithubCreatePullRequestPanel.getBranch();
  }

  @Override
  protected void doOKAction() {
    super.doOKAction();
    GithubProjectSettings.getInstance(myProject).setCreatePullRequestDefaultBranch(getTargetBranch());
    myWorker.performAction(getTitle(), getDescription(), getTargetBranch());
  }

  @Nullable
  @Override
  protected ValidationInfo doValidate() {
    if (StringUtil.isEmptyOrSpaces(getRequestTitle())) {
      return new ValidationInfo("Title can't be empty'", myGithubCreatePullRequestPanel.getTitleTextField());
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
