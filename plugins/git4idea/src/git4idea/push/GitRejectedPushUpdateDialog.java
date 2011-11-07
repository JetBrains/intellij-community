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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBLabel;
import git4idea.GitBranch;
import git4idea.GitUtil;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.Collection;

import static git4idea.ui.GitUIUtil.code;

/**
 * @author Kirill Likhodedov
 */
class GitRejectedPushUpdateDialog extends DialogWrapper {

  static final int MERGE_EXIT_CODE = NEXT_USER_EXIT_CODE;
  static final int REBASE_EXIT_CODE = MERGE_EXIT_CODE + 1;

  private final Project myProject;
  private final Collection<GitRepository> myRepositories;
  private final JCheckBox myUpdateAllRoots;

  protected GitRejectedPushUpdateDialog(@NotNull Project project, @NotNull Collection<GitRepository> repositories) {
    super(project);
    myProject = project;
    myRepositories = repositories;

    myUpdateAllRoots = new JCheckBox("Update all repositories", true);

    init();
    setTitle("Push Rejected");
  }

  @Override
  protected JComponent createCenterPanel() {
    JBLabel desc = new JBLabel(makeDescription());
    
    JCheckBox dontAskAgain = new JCheckBox("Remember the update method choice and don't ask again (you may change this in the Settings)");
    JPanel options = new JPanel(new BorderLayout());
    options.add(dontAskAgain, BorderLayout.SOUTH);
    
    if (!GitUtil.justOneGitRepository(myProject)) {
      options.add(myUpdateAllRoots);
    }

    JPanel rootPanel = new JPanel(new BorderLayout());
    rootPanel.add(desc);
    rootPanel.add(options, BorderLayout.SOUTH);
    return rootPanel;
  }

  private String makeDescription() {
    if (GitUtil.justOneGitRepository(myProject)) {
      assert !myRepositories.isEmpty() : "repositories are empty";
      GitRepository repository = myRepositories.iterator().next();
      GitBranch currentBranch = getCurrentBranch(repository);

      return "<html>Push of branch " + code(currentBranch.getName()) + " was rejected. <br/>" +
             "You need to merge remote changes before pushing again.";
    }
    else if (myRepositories.size() == 1) {  // there are more than 1 repositories in the project, but only one was rejected
      GitRepository repository = myRepositories.iterator().next();
      GitBranch currentBranch = getCurrentBranch(repository);

      return "<html>Push of branch " + code(currentBranch.getName()) + " in repository " + code(repository.getPresentableUrl()) + "was rejected. <br/>" +
             "You need to merge remote changes before pushing again.";
    }
    else {  // several repositories rejected the push at once
      StringBuilder sb = new StringBuilder();
      sb.append("<html>Push of current branch was rejected: <br/>");
      for (GitRepository repository : myRepositories) {
        GitBranch currentBranch = getCurrentBranch(repository);
        sb.append(code(currentBranch.getName()) + " in " + code(repository.getPresentableUrl()) + "<br/>");
      }
      return sb.toString();
    }
  }

  public boolean updateAll() {
    return myUpdateAllRoots.isSelected();
  }

  @NotNull
  private static GitBranch getCurrentBranch(GitRepository repository) {
    GitBranch currentBranch = repository.getCurrentBranch();
    assert currentBranch != null : "Current branch can't be null here. " + repository;
    return currentBranch;
  }

  @Override
  protected Action[] createActions() {
    return new Action[] { getCancelAction(), new MergeAction(this), new RebaseAction(this) };
  }

  @Override
  protected Action getHelpAction() {
    return super.getHelpAction();
  }



  private static class MergeAction extends AbstractAction {


    private final DialogWrapper myDialog;

    MergeAction(DialogWrapper dialog) {
      super("Merge");
      myDialog = dialog;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      myDialog.close(MERGE_EXIT_CODE);
    }
  }

  private static class RebaseAction extends AbstractAction {

    private final DialogWrapper myDialog;

    RebaseAction(DialogWrapper dialog) {
      super("Rebase");
      myDialog = dialog;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      myDialog.close(REBASE_EXIT_CODE);
    }
  }

}
