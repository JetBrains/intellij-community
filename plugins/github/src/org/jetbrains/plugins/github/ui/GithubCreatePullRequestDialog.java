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

import com.intellij.CommonBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.plugins.github.GithubCreatePullRequestWorker;
import org.jetbrains.plugins.github.api.GithubFullPath;
import org.jetbrains.plugins.github.util.GithubNotifications;
import org.jetbrains.plugins.github.util.GithubProjectSettings;
import org.jetbrains.plugins.github.util.GithubSettings;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Collections;

import static org.jetbrains.plugins.github.GithubCreatePullRequestWorker.BranchInfo;
import static org.jetbrains.plugins.github.GithubCreatePullRequestWorker.ForkInfo;

/**
 * @author Aleksey Pivovarov
 */
public class GithubCreatePullRequestDialog extends DialogWrapper {
  @NotNull private final GithubCreatePullRequestPanel myPanel;
  @NotNull private final GithubCreatePullRequestWorker myWorker;
  @NotNull private final GithubProjectSettings myProjectSettings;
  @NotNull private static final CreateRemoteDoNotAskOption ourDoNotAskOption = new CreateRemoteDoNotAskOption();

  public GithubCreatePullRequestDialog(@NotNull final Project project, @NotNull GithubCreatePullRequestWorker worker) {
    super(project, true);
    myWorker = worker;

    myProjectSettings = GithubProjectSettings.getInstance(project);

    myPanel = new GithubCreatePullRequestPanel();
    myPanel.getShowDiffButton().addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myWorker.showDiffDialog(myPanel.getSelectedBranch());
      }
    });
    myPanel.getSelectForkButton().addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        ForkInfo fork = myWorker.showTargetDialog();
        if (fork != null) {
          myPanel.setForks(myWorker.getForks());
          myPanel.setSelectedFork(fork.getPath());
        }
      }
    });

    myPanel.getForkComboBox().addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        if (e.getStateChange() == ItemEvent.DESELECTED) {
          myPanel.setBranches(Collections.<BranchInfo>emptyList());
        }
        if (e.getStateChange() == ItemEvent.SELECTED) {
          final ForkInfo fork = (ForkInfo)e.getItem();
          if (fork == null) return;

          myPanel.setBranches(fork.getBranches());
          myPanel.setSelectedBranch(fork.getDefaultBranch());

          if (fork.getRemoteName() == null && !fork.isProposedToCreateRemote()) {
            fork.setProposedToCreateRemote(true);
            boolean createRemote = false;

            switch (GithubSettings.getInstance().getCreatePullRequestCreateRemote()) {
              case YES:
                createRemote = true;
                break;
              case NO:
                createRemote = false;
                break;
              case UNSURE:
                createRemote = GithubNotifications.showYesNoDialog(project,
                                                                   "Can't Find Remote",
                                                                   "Configure remote for '" + fork.getPath().getUser() + "'?",
                                                                   ourDoNotAskOption);
                break;
            }

            if (createRemote) {
              myWorker.configureRemote(fork);
            }
          }

          if (fork.getRemoteName() == null) {
            myPanel.setDiffEnabled(false);
          }
          else {
            myPanel.setDiffEnabled(true);
            myWorker.launchFetchRemote(fork);
          }
        }
      }
    });

    myPanel.getBranchComboBox().addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        if (e.getStateChange() == ItemEvent.SELECTED) {
          BranchInfo branch = (BranchInfo)e.getItem();
          if (branch == null) return;

          if (branch.getForkInfo().getRemoteName() != null) {
            if (branch.getDiffInfoTask() != null && branch.getDiffInfoTask().isDone() && branch.getDiffInfoTask().safeGet() == null) {
              myPanel.setDiffEnabled(false);
            }
            else {
              myPanel.setDiffEnabled(true);
            }
          }

          if (myPanel.isTitleDescriptionEmptyOrNotModified()) {
            Pair<String, String> description = myWorker.getDefaultDescriptionMessage(branch);
            myPanel.setTitle(description.getFirst());
            myPanel.setDescription(description.getSecond());
          }

          myWorker.launchLoadDiffInfo(branch);
        }
      }
    });

    myPanel.setForks(myWorker.getForks());

    GithubFullPath defaultRepo = myProjectSettings.getCreatePullRequestDefaultRepo();
    String defaultBranch = myProjectSettings.getCreatePullRequestDefaultBranch();
    myPanel.setSelectedFork(defaultRepo);
    if (defaultBranch != null) { // do not rewrite default value of Fork.getDefaultBranch() by null
      myPanel.setSelectedBranch(defaultBranch);
    }

    setTitle("Create Pull Request - " + myWorker.getCurrentBranch());
    init();
  }

  @Override
  protected void doOKAction() {
    BranchInfo branch = myPanel.getSelectedBranch();
    if (myWorker.checkAction(branch)) {
      assert branch != null;
      myWorker.createPullRequest(branch, getRequestTitle(), getDescription());

      myProjectSettings.setCreatePullRequestDefaultBranch(branch.getRemoteName());
      myProjectSettings.setCreatePullRequestDefaultRepo(branch.getForkInfo().getPath());

      super.doOKAction();
    }
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myPanel.getPanel();
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myPanel.getPreferredComponent();
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
    return myPanel.getTitle();
  }

  @NotNull
  private String getDescription() {
    return myPanel.getDescription();
  }

  @Nullable
  @Override
  protected ValidationInfo doValidate() {
    if (StringUtil.isEmptyOrSpaces(getRequestTitle())) {
      return new ValidationInfo("Title can't be empty'", myPanel.getTitleTextField());
    }
    return null;
  }

  @TestOnly
  public void testSetRequestTitle(String title) {
    myPanel.setTitle(title);
  }

  @TestOnly
  public void testSetBranch(String branch) {
    myPanel.setSelectedBranch(branch);
  }

  @TestOnly
  public void testCreatePullRequest() {
    myWorker.createPullRequest(myPanel.getSelectedBranch(), getRequestTitle(), getDescription());
  }

  @TestOnly
  public void testSetFork(@NotNull GithubFullPath forkPath) {
    myPanel.setSelectedFork(forkPath);
  }

  private static class CreateRemoteDoNotAskOption implements DoNotAskOption {
    @Override
    public boolean isToBeShown() {
      return true;
    }

    @Override
    public void setToBeShown(boolean value, int exitCode) {
      if (value) {
        GithubSettings.getInstance().setCreatePullRequestCreateRemote(ThreeState.UNSURE);
      }
      else if (exitCode == DialogWrapper.OK_EXIT_CODE) {
        GithubSettings.getInstance().setCreatePullRequestCreateRemote(ThreeState.YES);
      }
      else {
        GithubSettings.getInstance().setCreatePullRequestCreateRemote(ThreeState.NO);
      }
    }

    @Override
    public boolean canBeHidden() {
      return true;
    }

    @Override
    public boolean shouldSaveOptionsOnCancel() {
      return false;
    }

    @NotNull
    @Override
    public String getDoNotShowMessage() {
      return CommonBundle.message("dialog.options.do.not.ask");
    }
  }
}
