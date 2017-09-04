/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.ide.ui.customization.CustomActionsSchema;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Roman.Chernyatchik
 * <p>
 * Context aware VCS actions quick list.
 * May be customized using com.intellij.openapi.vcs.actions.VcsQuickListContentProvider extension point.
 */
public class VcsQuickListPopupAction extends QuickSwitchSchemeAction implements DumbAware {

  public enum SupportedVCS {VCS, NOT_IN_VCS}

  public VcsQuickListPopupAction() {
    myActionPlace = ActionPlaces.ACTION_PLACE_VCS_QUICK_LIST_POPUP_ACTION;
  }

  protected String getPopupTitle(AnActionEvent e) {
    return VcsBundle.message("vcs.quicklist.popup.title");
  }

  protected void fillActions(@Nullable Project project,
                             @NotNull DefaultActionGroup group,
                             @NotNull DataContext dataContext) {
    if (project == null) return;
    CustomActionsSchema schema = CustomActionsSchema.getInstance();
    group.add(schema.getCorrectedAction("Vcs.Operations.Popup"));
  }

  @NotNull
  private static Pair<SupportedVCS, AbstractVcs> getActiveVCS(@NotNull Project project, @NotNull DataContext dataContext) {
    ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
    AbstractVcs[] activeVcss = vcsManager.getAllActiveVcss();
    if (activeVcss.length == 0) {
      return new Pair<>(SupportedVCS.NOT_IN_VCS, null);
    }
    else if (activeVcss.length == 1) {
      return Pair.create(SupportedVCS.VCS, activeVcss[0]);
    }
    VirtualFile file = CommonDataKeys.VIRTUAL_FILE.getData(dataContext);
    AbstractVcs vscForFile = file != null ? ProjectLevelVcsManager.getInstance(project).getVcsFor(file) : null;
    return vscForFile != null ? Pair.create(SupportedVCS.VCS, vscForFile) : Pair.create(SupportedVCS.VCS, null);
  }

  public static class Providers extends ActionGroup implements DumbAware {
    @NotNull
    @Override
    public AnAction[] getChildren(@Nullable AnActionEvent e) {
      if (e == null) return EMPTY_ARRAY;
      Project project = e.getProject();
      DataContext dataContext = e.getDataContext();
      Pair<SupportedVCS, AbstractVcs> pair = project == null ? Pair.empty() : getActiveVCS(project, dataContext);
      JBIterable<VcsQuickListContentProvider> providers = JBIterable.of(VcsQuickListContentProvider.EP_NAME.getExtensions());
      JBIterable<AnAction> actions;
      if (pair.first == SupportedVCS.NOT_IN_VCS) {
        actions = providers.flatMap(p -> p.getNotInVcsActions(project, dataContext));
      }
      else if (pair.second != null) {
        AbstractVcs vcs = pair.second;
        List<AnAction> replacingActions = providers
          .filterMap(p -> p.replaceVcsActionsFor(vcs, dataContext) ? p.getVcsActions(project, vcs, dataContext) : null)
          .first();
        actions = replacingActions != null ? JBIterable.from(replacingActions) :
                  providers.flatMap(p -> p.getVcsActions(project, vcs, dataContext));
      }
      else actions = JBIterable.empty();
      return actions.toList().toArray(EMPTY_ARRAY);
    }
  }

  public final static class VcsNameSeparator extends ActionGroup implements DumbAware {
    @NotNull
    @Override
    public AnAction[] getChildren(@Nullable AnActionEvent e) {
      if (e == null) return EMPTY_ARRAY;
      Project project = e.getProject();
      Pair<SupportedVCS, AbstractVcs> pair = project == null ? Pair.empty() : getActiveVCS(project, e.getDataContext());
      if (pair.first != SupportedVCS.VCS || pair.second == null) {
        return EMPTY_ARRAY;
      }
      else {
        return new AnAction[] { Separator.create(pair.second.getDisplayName()) };
      }
    }
  }

  public static class VcsAware extends DefaultActionGroup implements DumbAware {
    @Override
    public void update(AnActionEvent e) {
      Project project = e.getProject();
      DataContext dataContext = e.getDataContext();
      Pair<SupportedVCS, AbstractVcs> pair = project == null ? Pair.empty() : getActiveVCS(project, dataContext);
      if (pair.first == SupportedVCS.VCS) {
        AbstractVcs vcs = pair.second;
        JBIterable<VcsQuickListContentProvider> providers = JBIterable.of(VcsQuickListContentProvider.EP_NAME.getExtensions());
        List<AnAction> replacingActions = providers
          .filterMap(p -> p.replaceVcsActionsFor(vcs, dataContext) ? p.getVcsActions(project, vcs, dataContext) : null)
          .first();
        e.getPresentation().setVisible(replacingActions == null);
      }
    }
  }

  public static class NonVcsAware extends DefaultActionGroup implements DumbAware {
    @Override
    public void update(AnActionEvent e) {
      Project project = e.getProject();
      Pair<SupportedVCS, AbstractVcs> pair = project == null ? Pair.empty() : getActiveVCS(project, e.getDataContext());
      e.getPresentation().setVisible(pair.first == SupportedVCS.NOT_IN_VCS);
    }
  }
}
