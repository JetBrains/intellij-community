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
package org.zmlx.hg4idea.branch;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.branch.DvcsBranchPopup;
import com.intellij.dvcs.repo.AbstractRepositoryManager;
import com.intellij.dvcs.ui.RootAction;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgProjectSettings;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.repo.HgRepositoryManager;
import org.zmlx.hg4idea.util.HgUtil;

import javax.swing.*;
import java.util.List;

/**
 * <p>
 * The popup which allows to quickly switch and control Hg branches.
 * </p>
 * <p>
 * Use {@link #asListPopup()} to achieve the {@link com.intellij.openapi.ui.popup.ListPopup} itself.
 * </p>
 */
public class HgBranchPopup extends DvcsBranchPopup<HgRepository> {

  /**
   * @param currentRepository Current repository, which means the repository of the currently open or selected file.
   */
  public static HgBranchPopup getInstance(@NotNull Project project, @NotNull HgRepository currentRepository) {

    HgRepositoryManager manager = HgUtil.getRepositoryManager(project);
    HgProjectSettings hgProjectSettings = ServiceManager.getService(project, HgProjectSettings.class);
    HgMultiRootBranchConfig hgMultiRootBranchConfig = new HgMultiRootBranchConfig(manager.getRepositories());

    Condition<AnAction> preselectActionCondition = new Condition<AnAction>() {
      @Override
      public boolean value(AnAction action) {
        return false;
      }
    };
    return new HgBranchPopup(currentRepository, manager, hgMultiRootBranchConfig, hgProjectSettings,
                             preselectActionCondition);
  }

  private HgBranchPopup(@NotNull HgRepository currentRepository,
                        @NotNull HgRepositoryManager repositoryManager,
                        @NotNull HgMultiRootBranchConfig hgMultiRootBranchConfig, @NotNull HgProjectSettings vcsSettings,
                        @NotNull Condition<AnAction> preselectActionCondition) {
    super(currentRepository, repositoryManager, hgMultiRootBranchConfig, vcsSettings, preselectActionCondition);
  }

  protected void setCurrentBranchInfo() {
    String branchText = "Current branch : ";
    //always display heavy branch name for additional info //
    myPopup.setAdText(branchText + myCurrentRepository.getCurrentBranch(), SwingConstants.CENTER);
  }

  @Override
  protected void fillWithCommonRepositoryActions(@NotNull DefaultActionGroup popupGroup,
                                                 @NotNull AbstractRepositoryManager<HgRepository> repositoryManager) {
    List<HgRepository> allRepositories = repositoryManager.getRepositories();
    popupGroup.add(new HgBranchPopupActions.HgNewBranchAction(myProject, allRepositories, myCurrentRepository));
    popupGroup.addAction(new HgBranchPopupActions.HgNewBookmarkAction(allRepositories, myCurrentRepository));
    popupGroup.addAction(new HgBranchPopupActions.HgCloseBranchAction(allRepositories, myCurrentRepository));
    popupGroup.addAction(new HgBranchPopupActions.HgShowUnnamedHeadsForCurrentBranchAction(myCurrentRepository));
    popupGroup.addAll(createRepositoriesActions());

    popupGroup.addSeparator("Common Branches");
    for (String branch : myMultiRootBranchConfig.getLocalBranchNames()) {
      List<HgRepository> repositories = filterRepositoriesNotOnThisBranch(branch, allRepositories);
      if (!repositories.isEmpty()) {
        popupGroup.add(new HgCommonBranchActions(myProject, repositories, branch));
      }
    }
    popupGroup.addSeparator("Common Bookmarks");
    for (String branch : ((HgMultiRootBranchConfig)myMultiRootBranchConfig).getBookmarkNames()) {
      List<HgRepository> repositories = filterRepositoriesNotOnThisBranch(branch, allRepositories);
      if (!repositories.isEmpty()) {
        popupGroup.add(new HgBranchPopupActions.BookmarkActions(myProject, repositories, branch));
      }
    }
  }

  @NotNull
  protected DefaultActionGroup createRepositoriesActions() {
    DefaultActionGroup popupGroup = new DefaultActionGroup(null, false);
    popupGroup.addSeparator("Repositories");
    for (HgRepository repository : DvcsUtil.sortRepositories(myRepositoryManager.getRepositories())) {
      popupGroup.add(new RootAction<>(repository, highlightCurrentRepo() ? myCurrentRepository : null,
                                      new HgBranchPopupActions(repository.getProject(), repository).createActions(null),
                                      HgUtil.getDisplayableBranchOrBookmarkText(repository)));
    }
    return popupGroup;
  }

  protected void fillPopupWithCurrentRepositoryActions(@NotNull DefaultActionGroup popupGroup, @Nullable DefaultActionGroup actions) {
    popupGroup.addAll(new HgBranchPopupActions(myProject, myCurrentRepository).createActions(actions));
  }
}

