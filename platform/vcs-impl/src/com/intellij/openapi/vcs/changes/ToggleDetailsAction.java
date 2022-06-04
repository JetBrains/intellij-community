package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.changes.actions.ShowDiffPreviewAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ToggleDetailsAction extends ShowDiffPreviewAction {

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    Project project = e.getProject();
    if (project == null) return;
    ChangesViewManager changesViewManager = getChangesViewManager(project);
    if (changesViewManager == null) return;
    e.getPresentation().setEnabledAndVisible(changesViewManager.isDiffPreviewAvailable());
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    Project project = e.getProject();
    if (project == null) return;
    VcsConfiguration.getInstance(project).LOCAL_CHANGES_DETAILS_PREVIEW_SHOWN = state;

    ChangesViewManager changesViewManager = getChangesViewManager(project);
    if (changesViewManager == null) return;
    changesViewManager.diffPreviewChanged(state);
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return false;

    return VcsConfiguration.getInstance(project).LOCAL_CHANGES_DETAILS_PREVIEW_SHOWN;
  }

  @Nullable
  private static ChangesViewManager getChangesViewManager(@NotNull Project project) {
    ChangesViewI changesView = ChangesViewManager.getInstance(project);
    if (changesView instanceof ChangesViewManager) return (ChangesViewManager)changesView;
    return null;
  }
}
