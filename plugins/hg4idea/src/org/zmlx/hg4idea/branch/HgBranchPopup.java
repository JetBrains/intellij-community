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
import com.intellij.dvcs.MultiRootBranches;
import com.intellij.dvcs.branch.DvcsBranchPopup;
import com.intellij.dvcs.repo.AbstractRepositoryManager;
import com.intellij.dvcs.repo.Repository;
import com.intellij.dvcs.ui.RootAction;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgProjectSettings;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.repo.HgRepositoryManager;
import org.zmlx.hg4idea.util.HgUtil;

import java.util.List;
import java.util.Objects;

import static com.intellij.dvcs.branch.DvcsBranchPopup.MyMoreIndex.DEFAULT_NUM;
import static com.intellij.dvcs.branch.DvcsBranchPopup.MyMoreIndex.MAX_NUM;
import static com.intellij.dvcs.ui.BranchActionGroupPopup.wrapWithMoreActionIfNeeded;
import static com.intellij.dvcs.ui.BranchActionUtil.FAVORITE_BRANCH_COMPARATOR;
import static com.intellij.dvcs.ui.BranchActionUtil.getNumOfTopShownBranches;
import static java.util.stream.Collectors.toList;
import static org.zmlx.hg4idea.util.HgUtil.getDisplayableBranchOrBookmarkText;


/**
 * <p>
 * The popup which allows to quickly switch and control Hg branches.
 * </p>
 * <p>
 * Use {@link #asListPopup()} to achieve the {@link com.intellij.openapi.ui.popup.ListPopup} itself.
 * </p>
 */
public class HgBranchPopup extends DvcsBranchPopup<HgRepository> {
  private static final String DIMENSION_SERVICE_KEY = "Hg.Branch.Popup";
  static final String SHOW_ALL_BRANCHES_KEY = "Hg.Branch.Popup.ShowAllBranches";
  static final String SHOW_ALL_BOOKMARKS_KEY = "Hg.Branch.Popup.ShowAllBookmarks";
  static final String SHOW_ALL_REPOSITORIES = "Hg.Branch.Popup.ShowAllRepositories";

  /**
   * @param currentRepository Current repository, which means the repository of the currently open or selected file.
   */
  public static HgBranchPopup getInstance(@NotNull Project project, @NotNull HgRepository currentRepository) {

    HgRepositoryManager manager = HgUtil.getRepositoryManager(project);
    HgProjectSettings hgProjectSettings = ServiceManager.getService(project, HgProjectSettings.class);
    HgMultiRootBranchConfig hgMultiRootBranchConfig = new HgMultiRootBranchConfig(manager.getRepositories());

    return new HgBranchPopup(currentRepository, manager, hgMultiRootBranchConfig, hgProjectSettings, Conditions.alwaysFalse());
  }

  private HgBranchPopup(@NotNull HgRepository currentRepository,
                        @NotNull HgRepositoryManager repositoryManager,
                        @NotNull HgMultiRootBranchConfig hgMultiRootBranchConfig, @NotNull HgProjectSettings vcsSettings,
                        @NotNull Condition<AnAction> preselectActionCondition) {
    super(currentRepository, repositoryManager, hgMultiRootBranchConfig, vcsSettings, preselectActionCondition, DIMENSION_SERVICE_KEY);
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
    List<HgCommonBranchActions> branchActions =
      myMultiRootBranchConfig.getLocalBranchNames().stream()
        .map(b -> createLocalBranchActions(allRepositories, b, false))
        .filter(Objects::nonNull).sorted(FAVORITE_BRANCH_COMPARATOR).collect(toList());
    int topShownBranches = getNumOfTopShownBranches(branchActions);
    String commonBranch = MultiRootBranches.getCommonName(myRepositoryManager.getRepositories(), Repository::getCurrentBranchName);
    if (commonBranch != null) {
      branchActions.add(0, new HgBranchPopupActions.CurrentBranch(myProject, allRepositories, commonBranch));
      topShownBranches++;
    }
    wrapWithMoreActionIfNeeded(myProject, popupGroup, branchActions, topShownBranches, SHOW_ALL_BRANCHES_KEY, true);

    popupGroup.addSeparator("Common Bookmarks");
    List<HgCommonBranchActions> bookmarkActions = ((HgMultiRootBranchConfig)myMultiRootBranchConfig).getBookmarkNames().stream()
      .map(bm -> createLocalBranchActions(allRepositories, bm, true))
      .filter(Objects::nonNull).sorted(FAVORITE_BRANCH_COMPARATOR).collect(toList());
    int topShownBookmarks = getNumOfTopShownBranches(bookmarkActions);
    String commonBookmark = MultiRootBranches.getCommonName(repositoryManager.getRepositories(), HgRepository::getCurrentBookmark);
    if (commonBookmark != null) {
      bookmarkActions.add(0, new HgBranchPopupActions.CurrentActiveBookmark(myProject, allRepositories, commonBookmark));
      topShownBookmarks++;
    }
    wrapWithMoreActionIfNeeded(myProject, popupGroup, bookmarkActions, topShownBookmarks, SHOW_ALL_BOOKMARKS_KEY, true);
  }

  @Nullable
  private HgCommonBranchActions createLocalBranchActions(List<HgRepository> allRepositories, String name, boolean isBookmark) {
    List<HgRepository> repositories = filterRepositoriesNotOnThisBranch(name, allRepositories);
    if (repositories.isEmpty()) return null;
    return isBookmark
           ? new HgBranchPopupActions.BookmarkActions(myProject, repositories, name)
           : new HgBranchPopupActions.BranchActions(myProject, repositories, name);
  }

  @NotNull
  protected DefaultActionGroup createRepositoriesActions() {
    DefaultActionGroup popupGroup = new DefaultActionGroup(null, false);
    popupGroup.addSeparator("Repositories");
    List<ActionGroup> rootActions = DvcsUtil.sortRepositories(myRepositoryManager.getRepositories()).stream()
      .map(repo -> new RootAction<>(repo, new HgBranchPopupActions(repo.getProject(), repo).createActions(),
                                    getDisplayableBranchOrBookmarkText(repo)))
      .collect(toList());
    wrapWithMoreActionIfNeeded(myProject, popupGroup, rootActions, rootActions.size() > MAX_NUM ? DEFAULT_NUM : MAX_NUM,
                               SHOW_ALL_REPOSITORIES);
    return popupGroup;
  }

  protected void fillPopupWithCurrentRepositoryActions(@NotNull DefaultActionGroup popupGroup, @Nullable DefaultActionGroup actions) {
    popupGroup.addAll(new HgBranchPopupActions(myProject, myCurrentRepository).createActions(actions, myRepoTitleInfo, true));
  }
}

