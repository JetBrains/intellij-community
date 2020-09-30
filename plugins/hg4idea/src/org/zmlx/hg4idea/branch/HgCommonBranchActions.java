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

import com.intellij.dvcs.ui.BranchActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgBundle;
import org.zmlx.hg4idea.command.HgMergeCommand;
import org.zmlx.hg4idea.command.HgUpdateCommand;
import org.zmlx.hg4idea.repo.HgRepository;

import java.util.List;

public class HgCommonBranchActions extends BranchActionGroup {

  @NotNull protected final Project myProject;
  @NotNull private final HgBranchManager myBranchManager;
  @NotNull protected final @NlsSafe String myBranchName;
  @NotNull protected final List<HgRepository> myRepositories;
  @Nullable private final HgBranchType myBranchType;

  HgCommonBranchActions(@NotNull Project project, @NotNull List<HgRepository> repositories, @NotNull @NlsSafe String branchName) {
    this(project, repositories, branchName, null);
  }

  HgCommonBranchActions(@NotNull Project project,
                        @NotNull List<HgRepository> repositories,
                        @NotNull @NlsSafe String branchName,
                        @Nullable HgBranchType branchType) {
    myProject = project;
    myBranchName = branchName;
    myRepositories = repositories;
    myBranchManager = ServiceManager.getService(project, HgBranchManager.class);
    getTemplatePresentation().setText(myBranchName, false); // no mnemonics
    myBranchType = branchType;
    setFavorite(myBranchManager.isFavorite(myBranchType, chooseRepository(myRepositories), myBranchName));
    hideIconForUnnamedHeads();
  }

  private void hideIconForUnnamedHeads() {
    if (myBranchType == null) {
      getTemplatePresentation().setIcon(null);
      getTemplatePresentation().setHoveredIcon(null);
    }
  }

  @Nullable
  private static HgRepository chooseRepository(@NotNull List<? extends HgRepository> repositories) {
    assert !repositories.isEmpty();
    return repositories.size() > 1 ? null : repositories.get(0);
  }

  @Override
  public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
    return new AnAction[]{
      new UpdateAction(myProject, myRepositories, myBranchName),
      new CompareAction(myProject, myRepositories, myBranchName),
      new MergeAction(myProject, myRepositories, myBranchName)
    };
  }

  @Override
  public void toggle() {
    super.toggle();
    myBranchManager.setFavorite(myBranchType, chooseRepository(myRepositories), myBranchName, isFavorite());
  }

  private static class MergeAction extends HgBranchAbstractAction {

    MergeAction(@NotNull Project project,
                       @NotNull List<HgRepository> repositories,
                       @NotNull String branchName) {
      super(project, HgBundle.messagePointer("action.hg4idea.Merge"), repositories, branchName);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      FileDocumentManager.getInstance().saveAllDocuments();
      final UpdatedFiles updatedFiles = UpdatedFiles.create();
      for (final HgRepository repository : myRepositories) {
        HgMergeCommand.mergeWith(repository, myBranchName, updatedFiles);
      }
    }
  }

  private static class UpdateAction extends HgBranchAbstractAction {

    UpdateAction(@NotNull Project project,
                        @NotNull List<HgRepository> repositories,
                        @NotNull String branchName) {
      super(project, HgBundle.messagePointer("action.hg4idea.Update"), repositories, branchName);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      HgUpdateCommand.updateTo(myBranchName, myRepositories, null);
    }
  }

  private static class CompareAction extends HgBranchAbstractAction {
    CompareAction(@NotNull Project project,
                         @NotNull List<HgRepository> repositories,
                         @NotNull String branchName) {
      super(project, HgBundle.messagePointer("action.hg4idea.Compare"), repositories, branchName);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      FileDocumentManager.getInstance().saveAllDocuments();

      HgRepository repository = myRepositories.get(0);
      new HgBrancher(myProject).compare(myBranchName, myRepositories, repository);
    }
  }
}