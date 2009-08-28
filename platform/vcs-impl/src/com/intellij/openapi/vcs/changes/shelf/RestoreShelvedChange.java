package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsBundle;

public class RestoreShelvedChange extends AnAction {
  public RestoreShelvedChange() {
    super("Restore");
  }

  @Override
  public void update(final AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    final ShelvedChangeList[] recycledChanges = e.getData(ShelvedChangesViewManager.SHELVED_RECYCLED_CHANGELIST_KEY);
    e.getPresentation().setText(VcsBundle.message("vcs.shelf.action.restore.text"));
    e.getPresentation().setDescription(VcsBundle.message("vcs.shelf.action.restore.description"));
    e.getPresentation().setEnabled((project != null) && ((recycledChanges != null) && (recycledChanges.length == 1)));
  }

  public void actionPerformed(final AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    final ShelvedChangeList[] recycledChanges = e.getData(ShelvedChangesViewManager.SHELVED_RECYCLED_CHANGELIST_KEY);
    if (recycledChanges != null && recycledChanges.length == 1) {
      ShelveChangesManager.getInstance(project).restoreList(recycledChanges[0]);
    }
  }
}
