// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.conflicts;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.InplaceButton;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author Dmitry Avdeev
 */
public final class ChangelistConflictNotificationPanel extends EditorNotificationPanel {
  @Nullable
  public static ChangelistConflictNotificationPanel create(@NotNull Project project,
                                                           @NotNull VirtualFile file,
                                                           @NotNull FileEditor fileEditor) {
    final ChangeListManager manager = ChangeListManager.getInstance(project);
    final Change change = manager.getChange(file);
    if (change == null) return null;
    final LocalChangeList changeList = manager.getChangeList(change);
    if (changeList == null) return null;
    return new ChangelistConflictNotificationPanel(project, file, fileEditor, changeList);
  }

  private ChangelistConflictNotificationPanel(@NotNull Project project,
                                              @NotNull VirtualFile file,
                                              @NotNull FileEditor fileEditor,
                                              @NotNull LocalChangeList changeList) {
    super(fileEditor, EditorNotificationPanel.Status.Warning);

    ChangeListManager manager = ChangeListManager.getInstance(project);

    myLabel.setText(VcsBundle.message("changes.file.from.non.active.changelist.is.modified"));
    createActionLabel(VcsBundle.message("link.label.move.changes"),
                      () -> ChangelistConflictResolution.MOVE.resolveConflict(project, changeList.getChanges(), file))
      .setToolTipText(VcsBundle.message("changes.move.changes.to.active.change.list.name", manager.getDefaultChangeList().getName()));

    createActionLabel(VcsBundle.message("link.label.switch.changelist"),
                      () -> ChangeListManager.getInstance(project).setDefaultChangeList(changeList))
      .setToolTipText(VcsBundle.message("changes.set.active.changelist.to.change.list.name", changeList.getName()));

    createActionLabel(VcsBundle.message("link.label.ignore"),
                      () -> ChangelistConflictTracker.getInstance(project).ignoreConflict(file, true))
      .setToolTipText(VcsBundle.message("changes.hide.this.notification"));

    myLinksPanel.add(new InplaceButton(VcsBundle.message("tooltip.show.options.dialog"), AllIcons.General.Settings, new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        ShowSettingsUtil.getInstance().showSettingsDialog(project, ChangelistConflictConfigurable.class);
      }
    }));
  }
}
