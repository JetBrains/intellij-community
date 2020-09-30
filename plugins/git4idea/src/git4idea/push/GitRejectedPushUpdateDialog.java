// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.push;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.UIUtil;
import git4idea.GitBranch;
import git4idea.GitUtil;
import git4idea.config.UpdateMethod;
import git4idea.i18n.GitBundle;
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

import static com.intellij.openapi.util.text.HtmlChunk.text;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static com.intellij.xml.util.XmlStringUtil.wrapInHtml;
import static git4idea.i18n.GitBundleExtensions.html;

public class GitRejectedPushUpdateDialog extends DialogWrapper {

  public static final int MERGE_EXIT_CODE = NEXT_USER_EXIT_CODE;
  public static final int REBASE_EXIT_CODE = MERGE_EXIT_CODE + 1;

  private final Project myProject;
  private final Collection<? extends GitRepository> myRepositories;
  private final boolean myRebaseOverMergeProblemDetected;
  private final JCheckBox myUpdateAllRoots;
  private final RebaseAction myRebaseAction;
  private final MergeAction myMergeAction;
  private final JCheckBox myAutoUpdateInFuture;

  protected GitRejectedPushUpdateDialog(@NotNull Project project,
                                        @NotNull Collection<? extends GitRepository> repositories,
                                        @NotNull PushUpdateSettings initialSettings,
                                        boolean rebaseOverMergeProblemDetected) {
    super(project);
    myProject = project;
    myRepositories = repositories;
    myRebaseOverMergeProblemDetected = rebaseOverMergeProblemDetected;

    myUpdateAllRoots = new JCheckBox(GitBundle.message("push.rejected.update.not.rejected.repositories.as.well.checkbox"),
                                     initialSettings.shouldUpdateAllRoots());
    myAutoUpdateInFuture = new JCheckBox(html("push.rejected.remember.checkbox"));

    myMergeAction = new MergeAction();
    myRebaseAction = new RebaseAction();
    setDefaultAndFocusedActions(initialSettings.getUpdateMethod());
    init();
    setTitle(GitBundle.message("push.rejected.dialog.title"));
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

  private @NlsContexts.Label String makeDescription() {
    if (GitUtil.justOneGitRepository(myProject)) {
      assert !myRepositories.isEmpty() : "repositories are empty";
      GitRepository repository = myRepositories.iterator().next();
      String currentBranch = getCurrentBranch(repository).getName();
      return new HtmlBuilder()
        .appendRaw(GitBundle.message("push.rejected.only.one.git.repo", text(currentBranch).code())).br()
        .appendRaw(descriptionEnding())
        .toString();
    }
    else if (myRepositories.size() == 1) {  // there are more than 1 repositories in the project, but only one was rejected
      GitRepository repository = getFirstItem(myRepositories);
      String currentBranch = getCurrentBranch(repository).getName();

      return new HtmlBuilder()
        .appendRaw(
          GitBundle.message(
            "push.rejected.specific.repo",
            text(currentBranch).code(),
            text(repository.getPresentableUrl()).code()
          )
        ).br()
        .appendRaw(descriptionEnding())
        .toString();
    }
    else {  // several repositories rejected the push
      Map<GitRepository, GitBranch> currentBranches = getCurrentBranches();
      HtmlBuilder description = new HtmlBuilder();
      if (allBranchesHaveTheSameName(currentBranches)) {
        String branchName = getFirstItem(currentBranches.values()).getName();
        description.appendRaw(GitBundle.message("push.rejected.many.repos.single.branch", text(branchName).code())).br();
        for (GitRepository repository : DvcsUtil.sortRepositories(currentBranches.keySet())) {
          description.nbsp(4).append(text(repository.getPresentableUrl()).code()).br();
        }
      }
      else {
        description.append(GitBundle.message("push.rejected.many.repos")).br();
        for (Map.Entry<GitRepository, GitBranch> entry : currentBranches.entrySet()) {
          String repositoryUrl = entry.getKey().getPresentableUrl();
          String currentBranch = entry.getValue().getName();
          description
            .nbsp(4).appendRaw(GitBundle.message("push.rejected.many.repos.item", text(currentBranch).code(), text(repositoryUrl).code()))
            .br();
        }
      }
      return description.br()
        .appendRaw(descriptionEnding())
        .toString();
    }
  }

  @NotNull
  private @NlsContexts.Label String descriptionEnding() {
    if (myRebaseOverMergeProblemDetected) {
      return GitBundle.message("push.rejected.merge.needed.with.problem");
    }
    else {
      return GitBundle.message("push.rejected.merge.needed");
    }
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
  private static @NlsSafe GitBranch getCurrentBranch(GitRepository repository) {
    GitBranch currentBranch = repository.getCurrentBranch();
    assert currentBranch != null : "Current branch can't be null here. " + repository;
    return currentBranch;
  }

  @Override
  protected Action @NotNull [] createActions() {
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
      super(GitBundle.message("push.rejected.merge"));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      close(MERGE_EXIT_CODE);
    }
  }

  private class RebaseAction extends AbstractAction {

    RebaseAction() {
      super(myRebaseOverMergeProblemDetected ?
            GitBundle.message("push.rejected.rebase.anyway") :
            GitBundle.message("push.rejected.rebase"));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      close(REBASE_EXIT_CODE);
    }
  }

}
