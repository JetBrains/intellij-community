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
package org.zmlx.hg4idea.action;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.ui.BranchActionGroupPopup;
import com.intellij.dvcs.ui.RootAction;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.popup.list.ListPopupImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.repo.HgRepositoryImpl;
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
 *
 * @author NadyaZabrodina
 */
public class HgBranchPopup {

  private final Project myProject;

  private final HgRepository myCurrentRepository;
  private final ListPopupImpl myPopup;

  public ListPopup asListPopup() {
    return myPopup;
  }

  /**
   * @param currentRepository Current repository, which means the repository of the currently open or selected file.
   */
  public static HgBranchPopup getInstance(@NotNull Project project, @NotNull HgRepository currentRepository) {
    return new HgBranchPopup(project, currentRepository);
  }

  private HgBranchPopup(@NotNull Project project, @NotNull HgRepository currentRepository) {
    myProject = project;
    myCurrentRepository = currentRepository;
    String title = createPopupTitle(currentRepository);

    Condition<AnAction> preselectActionCondition = new Condition<AnAction>() {
      @Override
      public boolean value(AnAction action) {
        return false;
      }
    };
    myPopup = new BranchActionGroupPopup(title, project, preselectActionCondition, createActions());
    setCurrentBranchInfo();
  }


  @NotNull
  private static String createPopupTitle(@NotNull HgRepository currentRepository) {
    String title = "Hg Branches";
    title += " in " + DvcsUtil.getShortRepositoryName(currentRepository);
    return title;
  }

  private void setCurrentBranchInfo() {
    String branchText = "Current branch : ";
    myPopup.setAdText(branchText + HgUtil.getDisplayableBranchText(myCurrentRepository), SwingConstants.CENTER);
  }


  private ActionGroup createActions() {
    DefaultActionGroup popupGroup = new DefaultActionGroup(null, false);
    fillPopupWithCurrentRepositoryActions(popupGroup, createRepositoriesActions());
    popupGroup.addSeparator();
    return popupGroup;
  }


  @Nullable
  private DefaultActionGroup createRepositoriesActions() {
    List<VirtualFile> repositories = HgUtil.getHgRepositories(myProject);
    if (repositories.size() == 1) {
      return null; //  if project has only one repository all branches, bookmarks and actions should be inline  and no repository group needed
    }
    DefaultActionGroup popupGroup = new DefaultActionGroup(null, false);
    popupGroup.addSeparator("Repositories");
    boolean isMultiRepoConfig = repositories.size() > 1;
    for (VirtualFile repository : repositories) {
      HgRepository repo = HgRepositoryImpl.getInstance(repository, myProject, myProject);
      popupGroup.add(new RootAction<HgRepository>(repo, isMultiRepoConfig ? myCurrentRepository : null,
                                                  new HgBranchPopupActions(repo.getProject(), repo).createActions(null),
                                                  HgUtil.getDisplayableBranchText(repo)));
    }
    return popupGroup;
  }

  protected void fillPopupWithCurrentRepositoryActions(@NotNull DefaultActionGroup popupGroup, @Nullable DefaultActionGroup actions) {
    popupGroup.addAll(new HgBranchPopupActions(myProject, myCurrentRepository).createActions(actions));
  }
}

