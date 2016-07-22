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
package git4idea.push;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.UIUtil;
import git4idea.GitBranch;
import git4idea.GitUtil;
import git4idea.config.UpdateMethod;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static com.intellij.xml.util.XmlStringUtil.wrapInHtml;
import static git4idea.util.GitUIUtil.code;

/**
 * @author Kirill Likhodedov
 */
class GitRejectedPushUpdateDialog extends DialogWrapper {

  static final int MERGE_EXIT_CODE = NEXT_USER_EXIT_CODE;
  static final int REBASE_EXIT_CODE = MERGE_EXIT_CODE + 1;

  private static final String HTML_IDENT = "&nbsp;&nbsp;&nbsp;&nbsp;";
  public static final String DESCRIPTION_START = "Push of current branch ";

  private final Project myProject;
  private final Collection<GitRepository> myRepositories;
  private final boolean myRebaseOverMergeProblemDetected;
  private final JCheckBox myUpdateAllRoots;
  private final RebaseAction myRebaseAction;
  private final MergeAction myMergeAction;
  private final JCheckBox myAutoUpdateInFuture;

  protected GitRejectedPushUpdateDialog(@NotNull Project project,
                                        @NotNull Collection<GitRepository> repositories,
                                        @NotNull PushUpdateSettings initialSettings,
                                        boolean rebaseOverMergeProblemDetected) {
    super(project);
    myProject = project;
    myRepositories = repositories;
    myRebaseOverMergeProblemDetected = rebaseOverMergeProblemDetected;

    myUpdateAllRoots = new JCheckBox("Update not rejected repositories as well", initialSettings.shouldUpdateAllRoots());
    myUpdateAllRoots.setMnemonic('u');
    myAutoUpdateInFuture = new JCheckBox("<html>Remember the update method choice and <u>s</u>ilently update in future <br/>(you may change this in the Settings)</html>");
    myAutoUpdateInFuture.setMnemonic('s');

    myMergeAction = new MergeAction();
    myRebaseAction = new RebaseAction();
    setDefaultAndFocusedActions(initialSettings.getUpdateMethod());
    init();
    setTitle("Push Rejected");
  }

  private void setDefaultAndFocusedActions(@Nullable UpdateMethod updateMethod) {
    Action defaultAction;
    Action focusedAction;
    if (myRebaseOverMergeProblemDetected) {
      defaultAction = myMergeAction;
      focusedAction = getCancelAction();
    }
    else if (updateMethod == UpdateMethod.REBASE) {
      defaultAction = myRebaseAction;
      focusedAction = myMergeAction;
    }
    else {
      defaultAction = myMergeAction;
      focusedAction = myRebaseAction;
    }
    defaultAction.putValue(DEFAULT_ACTION, Boolean.TRUE);
    focusedAction.putValue(FOCUSED_ACTION, Boolean.TRUE);
  }

  @Override
  protected JComponent createCenterPanel() {
    JBLabel desc = new JBLabel(wrapInHtml(makeDescription()));

    JPanel options = new JPanel(new BorderLayout());
    if (!myRebaseOverMergeProblemDetected) {
      options.add(myAutoUpdateInFuture, BorderLayout.SOUTH);
    }

    if (!GitUtil.justOneGitRepository(myProject)) {
      options.add(myUpdateAllRoots);
    }

    final int GAP = 15;
    JPanel rootPanel = new JPanel(new BorderLayout(GAP, GAP));
    rootPanel.add(desc);
    rootPanel.add(options, BorderLayout.SOUTH);
    JLabel iconLabel = new JLabel(myRebaseOverMergeProblemDetected ? UIUtil.getWarningIcon() : UIUtil.getQuestionIcon());
    rootPanel.add(iconLabel, BorderLayout.WEST);

    return rootPanel;
  }

  @Override
  protected String getHelpId() {
    return "reference.VersionControl.Git.UpdateOnRejectedPushDialog";
  }

  private String makeDescription() {
    if (GitUtil.justOneGitRepository(myProject)) {
      assert !myRepositories.isEmpty() : "repositories are empty";
      GitRepository repository = myRepositories.iterator().next();
      GitBranch currentBranch = getCurrentBranch(repository);
      return DESCRIPTION_START + code(currentBranch.getName()) + " was rejected. <br/>" + descriptionEnding();
    }
    else if (myRepositories.size() == 1) {  // there are more than 1 repositories in the project, but only one was rejected
      GitRepository repository = myRepositories.iterator().next();
      GitBranch currentBranch = getCurrentBranch(repository);

      return DESCRIPTION_START + code(currentBranch.getName()) + " in repository <br/>" + code(repository.getPresentableUrl()) +
             " was rejected. <br/>" + descriptionEnding();
    }
    else {  // several repositories rejected the push
      Map<GitRepository, GitBranch> currentBranches = getCurrentBranches();
      if (allBranchesHaveTheSameName(currentBranches)) {
        String branchName = currentBranches.values().iterator().next().getName(); 
        StringBuilder sb = new StringBuilder(DESCRIPTION_START + code(branchName) + " was rejected in repositories <br/>");
        for (GitRepository repository : DvcsUtil.sortRepositories(currentBranches.keySet())) {
          sb.append(HTML_IDENT).append(code(repository.getPresentableUrl())).append("<br/>");
        }
        sb.append(descriptionEnding());
        return sb.toString();
      }
      else {
        StringBuilder sb = new StringBuilder("<html>Push of current branch was rejected: <br/>");
        for (Map.Entry<GitRepository, GitBranch> entry : currentBranches.entrySet()) {
          GitRepository repository = entry.getKey();
          GitBranch currentBranch = entry.getValue();
          sb.append(HTML_IDENT + code(currentBranch.getName()) + " in " + code(repository.getPresentableUrl()) + "<br/>");
        }
        sb.append(descriptionEnding());
        return sb.toString();
      }
    }
  }

  @NotNull
  private String descriptionEnding() {
    String desc = "Remote changes need to be merged before pushing.";
    if (myRebaseOverMergeProblemDetected) {
      desc += "<br/><br/>In this case <b>merge is highly recommended</b>, because there are non-pushed merge commits. " +
              "<br/>Rebasing them can lead to problems.";
    }
    return desc;
  }

  private static boolean allBranchesHaveTheSameName(@NotNull Map<GitRepository, GitBranch> branches) {
    String name = null;
    for (GitBranch branch : branches.values()) {
      if (name == null) {
        name = branch.getName();
      } else if (!name.equals(branch.getName())) {
        return false;
      }
    }
    return true;
  }

  @NotNull
  private Map<GitRepository, GitBranch> getCurrentBranches() {
    Map<GitRepository, GitBranch> currentBranches = new HashMap<>();
    for (GitRepository repository : myRepositories) {
      currentBranches.put(repository, getCurrentBranch(repository));
    }
    return currentBranches;
  }

  @NotNull
  private static GitBranch getCurrentBranch(GitRepository repository) {
    GitBranch currentBranch = repository.getCurrentBranch();
    assert currentBranch != null : "Current branch can't be null here. " + repository;
    return currentBranch;
  }

  @NotNull
  @Override
  protected Action[] createActions() {
    return new Action[] { getCancelAction(), myMergeAction, myRebaseAction};
  }

  boolean shouldUpdateAll() {
    return myUpdateAllRoots.isSelected();
  }

  boolean shouldAutoUpdateInFuture() {
    return myAutoUpdateInFuture.isSelected();
  }

  @TestOnly
  boolean warnsAboutRebaseOverMerge() {
    return myRebaseOverMergeProblemDetected;
  }

  @TestOnly
  @NotNull
  Action getDefaultAction() {
    return Boolean.TRUE.equals(myMergeAction.getValue(DEFAULT_ACTION)) ? myMergeAction : myRebaseAction;
  }

  private class MergeAction extends AbstractAction {
    MergeAction() {
      super("&Merge");
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      close(MERGE_EXIT_CODE);
    }
  }

  private class RebaseAction extends AbstractAction {

    RebaseAction() {
      super(myRebaseOverMergeProblemDetected ? "Rebase Anyway" : "&Rebase");
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      close(REBASE_EXIT_CODE);
    }
  }

}
