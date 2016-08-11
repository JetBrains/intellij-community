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
package com.intellij.openapi.vcs.actions;

import com.intellij.ide.actions.QuickSwitchSchemeAction;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Roman.Chernyatchik
 *
 * Context aware VCS actions quick list.
 * May be customized using com.intellij.openapi.vcs.actions.VcsQuickListContentProvider extension point.
 */
public class VcsQuickListPopupAction extends QuickSwitchSchemeAction implements DumbAware {

  public VcsQuickListPopupAction() {
    myActionPlace = ActionPlaces.ACTION_PLACE_VCS_QUICK_LIST_POPUP_ACTION;
  }

  protected void fillActions(@Nullable final Project project,
                             @NotNull final DefaultActionGroup group,
                             @NotNull final DataContext dataContext) {

    if (project == null) {
      return;
    }

    final Pair<SupportedVCS, AbstractVcs> typeAndVcs = getActiveVCS(project, dataContext);
    final AbstractVcs vcs = typeAndVcs.getSecond();
    final SupportedVCS popupType = typeAndVcs.getFirst();

    switch (popupType) {
      case VCS:
        fillVcsPopup(project, group, dataContext, vcs);
        break;

      case NOT_IN_VCS:
        fillNonInVcsActions(project, group, dataContext);
        break;
    }
  }

  protected boolean isEnabled() {
    return true;
  }

  private void fillVcsPopup(@NotNull final Project project,
                                    @NotNull final DefaultActionGroup group,
                                    @Nullable final DataContext dataContext,
                                    @Nullable final AbstractVcs vcs) {

    if (vcs != null) {
      // replace general vcs actions if necessary

      for (VcsQuickListContentProvider provider : VcsQuickListContentProvider.EP_NAME.getExtensions()) {
        if (provider.replaceVcsActionsFor(vcs, dataContext)) {
          final List<AnAction> actionsToReplace = provider.getVcsActions(project, vcs, dataContext);
          if (actionsToReplace != null) {
            // replace general actions with custom ones:
            addActions(actionsToReplace, group);
            // local history
            addLocalHistoryActions(group);
            return;
          }
        }
      }
    }

    // general list
    fillGeneralVcsPopup(project, group, dataContext, vcs);
  }

  private void fillGeneralVcsPopup(@NotNull final Project project,
                                   @NotNull final DefaultActionGroup group,
                                   @Nullable final DataContext dataContext,
                                   @Nullable final AbstractVcs vcs) {

    // include all custom actions in general popup
    final List<AnAction> actions = new ArrayList<>();
    for (VcsQuickListContentProvider provider : VcsQuickListContentProvider.EP_NAME.getExtensions()) {
      final List<AnAction> providerActions = provider.getVcsActions(project, vcs, dataContext);
      if (providerActions != null) {
        actions.addAll(providerActions);
      }
    }

    // basic operations
    addSeparator(group, vcs != null ? vcs.getDisplayName() : null);
    addAction("ChangesView.AddUnversioned", group);
    addAction("CheckinProject", group);
    addAction("CheckinFiles", group);
    addAction(IdeActions.CHANGES_VIEW_ROLLBACK, group);

    // history and compare
    addSeparator(group);
    addAction("Vcs.ShowTabbedFileHistory", group);
    addAction("Annotate", group);
    addAction("Compare.SameVersion", group);

    // custom actions
    addSeparator(group);
    addActions(actions, group);

    // additional stuff
    addSeparator(group);
    addAction(IdeActions.MOVE_TO_ANOTHER_CHANGE_LIST, group);

    // local history
    addLocalHistoryActions(group);
  }

  private void fillNonInVcsActions(@NotNull final Project project,
                                   @NotNull final DefaultActionGroup group,
                                   @Nullable final DataContext dataContext) {
    // add custom vcs actions
    for (VcsQuickListContentProvider provider : VcsQuickListContentProvider.EP_NAME.getExtensions()) {
      final List<AnAction> actions = provider.getNotInVcsActions(project, dataContext);
      if (actions != null) {
        addActions(actions, group);
      }
    }
    addSeparator(group);
    addAction("Start.Use.Vcs", group);
    addAction("Vcs.Checkout", group);

    // local history
    addLocalHistoryActions(group);
  }

  private void addLocalHistoryActions(DefaultActionGroup group) {
    addSeparator(group, VcsBundle.message("vcs.quicklist.pupup.section.local.history"));

    addAction("LocalHistory.ShowHistory", group);
    addAction("LocalHistory.PutLabel", group);
  }

  private void addActions(@NotNull final List<AnAction> actions,
                             @NotNull final DefaultActionGroup toGroup) {
    for (AnAction action : actions) {
      toGroup.addAction(action);
    }
  }

  private Pair<SupportedVCS, AbstractVcs> getActiveVCS(@NotNull final Project project, @Nullable final DataContext dataContext) {
    final AbstractVcs[] activeVcss = getActiveVCSs(project);
    if (activeVcss.length == 0) {
      // no vcs
      return new Pair<>(SupportedVCS.NOT_IN_VCS, null);
    } else if (activeVcss.length == 1) {
      // get by name
      return Pair.create(SupportedVCS.VCS, activeVcss[0]);
    }

    // by current file
    final VirtualFile file =  dataContext != null ? CommonDataKeys.VIRTUAL_FILE.getData(dataContext) : null;
    if (file != null) {
      final AbstractVcs vscForFile = ProjectLevelVcsManager.getInstance(project).getVcsFor(file);
      if (vscForFile != null) {
        return Pair.create(SupportedVCS.VCS, vscForFile);
      }
    }

    return new Pair<>(SupportedVCS.VCS, null);
  }

  private AbstractVcs[] getActiveVCSs(final Project project) {
    final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
    return vcsManager.getAllActiveVcss();
  }

  private void addAction(final String actionId, final DefaultActionGroup toGroup) {
    final AnAction action = ActionManager.getInstance().getAction(actionId);

    // add action to group if it is available
    if (action != null) {
      toGroup.add(action);
    }
  }

  private void addSeparator(final DefaultActionGroup toGroup) {
    addSeparator(toGroup, null);
  }

  private void addSeparator(final DefaultActionGroup toGroup, @Nullable final String title) {
    final Separator separator = title == null ? new Separator() : new Separator(title);
    toGroup.add(separator);
  }

  protected String getPopupTitle(AnActionEvent e) {
    return VcsBundle.message("vcs.quicklist.popup.title");
  }

  public enum SupportedVCS {
    VCS,
    NOT_IN_VCS
  }
}
