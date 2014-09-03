/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.zmlx.hg4idea.branch;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.HgVcsMessages;
import org.zmlx.hg4idea.action.HgAbstractGlobalAction;
import org.zmlx.hg4idea.action.HgCommandResultNotifier;
import org.zmlx.hg4idea.command.HgMergeCommand;
import org.zmlx.hg4idea.command.HgUpdateCommand;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.provider.update.HgConflictResolver;
import org.zmlx.hg4idea.provider.update.HgHeadMerger;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.util.HgErrorUtil;

import java.util.List;

public class HgCommonBranchActions extends ActionGroup {

  @NotNull protected final Project myProject;
  @NotNull protected String myBranchName;
  @NotNull List<HgRepository> myRepositories;

  HgCommonBranchActions(@NotNull Project project, @NotNull List<HgRepository> repositories, @NotNull String branchName) {
    super("", true);
    myProject = project;
    myBranchName = branchName;
    myRepositories = repositories;
    getTemplatePresentation().setText(myBranchName, false); // no mnemonics
  }

  @NotNull
  @Override
  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    return new AnAction[]{
      new UpdateAction(myProject, myRepositories, myBranchName),
      new MergeAction(myProject, myRepositories, myBranchName)
    };
  }

  private static class MergeAction extends HgBranchAbstractAction {

    public MergeAction(@NotNull Project project,
                       @NotNull List<HgRepository> repositories,
                       @NotNull String branchName) {
      super(project, "Merge", repositories, branchName);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      FileDocumentManager.getInstance().saveAllDocuments();
      final UpdatedFiles updatedFiles = UpdatedFiles.create();
      for (final HgRepository repository : myRepositories) {
        final HgMergeCommand hgMergeCommand = new HgMergeCommand(myProject, repository.getRoot());
        hgMergeCommand.setRevision(myBranchName);//there is no difference between branch or revision or bookmark as parameter to merge,
        // we need just a string
        new Task.Backgroundable(myProject, "Merging changes...") {
          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            try {
              new HgHeadMerger(myProject, hgMergeCommand)
                .merge(repository.getRoot());
              new HgConflictResolver(myProject, updatedFiles).resolve(repository.getRoot());
            }

            catch (VcsException exception) {
              assert myProject != null;  // myProject couldn't be null, see annotation for Merge action
              if (exception.isWarning()) {
                VcsNotifier.getInstance(myProject).notifyWarning("Warning during merge", exception.getMessage());
              }
              else {
                VcsNotifier.getInstance(myProject).notifyError("Exception during merge", exception.getMessage());
              }
            }
            catch (Exception e1) {
              HgAbstractGlobalAction.handleException(myProject, e1);
            }
          }
        }.queue();
      }
    }
  }

  private static class UpdateAction extends HgBranchAbstractAction {

    public UpdateAction(@NotNull Project project,
                        @NotNull List<HgRepository> repositories,
                        @NotNull String branchName) {
      super(project, "Update", repositories, branchName);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      FileDocumentManager.getInstance().saveAllDocuments();
      for (HgRepository repo : myRepositories) {
        final VirtualFile repository = repo.getRoot();
        final HgUpdateCommand hgUpdateCommand = new HgUpdateCommand(myProject, repository);
        hgUpdateCommand.setBranch(myBranchName);
        new Task.Backgroundable(myProject, HgVcsMessages.message("action.hg4idea.updateTo.description", myBranchName)) {
          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            HgCommandResult result = hgUpdateCommand.execute();
            assert myProject != null;  // myProject couldn't be null, see annotation for updateTo action
            if (HgErrorUtil.hasErrorsInCommandExecution(result)) {
              new HgCommandResultNotifier(myProject).notifyError(result, "", "Update failed");
              new HgConflictResolver(myProject).resolve(repository);
            }
            myProject.getMessageBus().syncPublisher(HgVcs.BRANCH_TOPIC).update(myProject, repository);
          }
        }.queue();
      }
    }
  }
}