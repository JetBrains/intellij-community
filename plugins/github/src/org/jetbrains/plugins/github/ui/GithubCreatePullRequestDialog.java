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

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Consumer;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.plugins.github.GithubCreatePullRequestWorker;
import org.jetbrains.plugins.github.api.GithubFullPath;
import org.jetbrains.plugins.github.util.GithubProjectSettings;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Collection;
import java.util.Collections;

/**
 * @author Aleksey Pivovarov
 */
public class GithubCreatePullRequestDialog extends DialogWrapper {
  @NotNull private final GithubCreatePullRequestPanel myGithubCreatePullRequestPanel;
  @NotNull private final GithubCreatePullRequestWorker myWorker;
  @NotNull private final GithubProjectSettings myProjectSettings;

  public GithubCreatePullRequestDialog(@NotNull GithubCreatePullRequestWorker worker) {
    super(worker.getProject(), true);
    myWorker = worker;

    myProjectSettings = GithubProjectSettings.getInstance(myWorker.getProject());

    myGithubCreatePullRequestPanel = new GithubCreatePullRequestPanel();

    myGithubCreatePullRequestPanel.getShowDiffButton().addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myWorker.showDiffDialog(myGithubCreatePullRequestPanel.getBranch());
      }
    });
    myGithubCreatePullRequestPanel.getSelectForkButton().addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        showTargetDialog();
      }
    });
    myGithubCreatePullRequestPanel.getBranchComboBox().addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        if (e.getStateChange() == ItemEvent.SELECTED) {
          if (myWorker.canShowDiff()) {
            myGithubCreatePullRequestPanel.setBusy(true);
            myWorker.getDiffDescriptionInPooledThread(getTargetBranch(), new Consumer<GithubCreatePullRequestWorker.DiffDescription>() {
              @Override
              public void consume(final GithubCreatePullRequestWorker.DiffDescription info) {
                UIUtil.invokeLaterIfNeeded(new Runnable() {
                  @Override
                  public void run() {
                    if (info == null) {
                      myGithubCreatePullRequestPanel.setBusy(false);
                      return;
                    }
                    if (getTargetBranch().equals(info.getBranch())) {
                      myGithubCreatePullRequestPanel.setBusy(false);
                      if (myGithubCreatePullRequestPanel.isTitleDescriptionEmptyOrNotModified()) {
                        myGithubCreatePullRequestPanel.setTitle(info.getTitle());
                        myGithubCreatePullRequestPanel.setDescription(info.getDescription());
                      }
                    }
                  }
                });
              }
            });
          }
        }
      }
    });

    setTitle("Create Pull Request - " + myWorker.getCurrentBranch());
    init();
  }

  @Override
  public void show() {
    GithubFullPath defaultForkPath = myProjectSettings.getCreatePullRequestDefaultRepo();
    if (defaultForkPath != null) {
      setTarget(defaultForkPath);
    }
    else {
      if (!showTargetDialog()) {
        close(CANCEL_EXIT_CODE);
        return;
      }
    }
    super.show();
  }

  private boolean showTargetDialog() {
    GithubFullPath forkPath = myWorker.showTargetDialog();
    if (forkPath == null) {
      return false;
    }
    return setTarget(forkPath);
  }

  private boolean setTarget(@NotNull GithubFullPath forkPath) {
    GithubCreatePullRequestWorker.GithubTargetInfo forkInfo = myWorker.setTarget(forkPath);
    if (forkInfo == null) {
      return false;
    }
    myProjectSettings.setCreatePullRequestDefaultRepo(forkPath);
    myGithubCreatePullRequestPanel.setDiffEnabled(myWorker.canShowDiff());
    updateBranches(forkInfo.getBranches(), forkPath);
    return true;
  }

  private void updateBranches(@NotNull Collection<String> branches, @NotNull GithubFullPath forkPath) {
    myGithubCreatePullRequestPanel.setBranches(branches);

    String configBranch = myProjectSettings.getCreatePullRequestDefaultBranch();
    if (configBranch != null) myGithubCreatePullRequestPanel.setSelectedBranch(configBranch);

    myGithubCreatePullRequestPanel.setForkName(forkPath.getFullName());
  }

  @Override
  protected void doOKAction() {
    if (myWorker.checkAction(getTargetBranch())) {
      myProjectSettings.setCreatePullRequestDefaultBranch(getTargetBranch());
      myWorker.performAction(getRequestTitle(), getDescription(), getTargetBranch());
      super.doOKAction();
    }
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

  @Nullable
  @Override
  protected ValidationInfo doValidate() {
    if (StringUtil.isEmptyOrSpaces(getRequestTitle())) {
      return new ValidationInfo("Title can't be empty'", myGithubCreatePullRequestPanel.getTitleTextField());
    }
    return null;
  }

  @TestOnly
  public void testSetRequestTitle(String title) {
    myGithubCreatePullRequestPanel.setTitle(title);
  }

  @TestOnly
  public void testSetBranch(String branch) {
    myGithubCreatePullRequestPanel.setBranches(Collections.singleton(branch));
  }

  @TestOnly
  public void testCreatePullRequest() {
    myWorker.performAction(getRequestTitle(), getDescription(), getTargetBranch());
  }

  @TestOnly
  public void testSetTarget(@NotNull GithubFullPath forkPath) {
    GithubCreatePullRequestWorker.GithubTargetInfo forkInfo = myWorker.setTarget(forkPath);
    if (forkInfo == null) {
      doCancelAction();
      return;
    }
    myGithubCreatePullRequestPanel.setDiffEnabled(myWorker.canShowDiff());
  }
}
