// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.conflicts;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.InplaceButton;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collections;

/**
* @author Dmitry Avdeev
*/
public final class ChangelistConflictNotificationPanel extends EditorNotificationPanel {

  private final ChangeList myChangeList;
  private final VirtualFile myFile;
  private final ChangelistConflictTracker myTracker;

  @Nullable
  public static ChangelistConflictNotificationPanel create(ChangelistConflictTracker tracker, @NotNull VirtualFile file, @NotNull FileEditor fileEditor) {
    final ChangeListManager manager = tracker.getChangeListManager();
    final Change change = manager.getChange(file);
    if (change == null) return null;
    final LocalChangeList changeList = manager.getChangeList(change);
    if (changeList == null) return null;
    return new ChangelistConflictNotificationPanel(tracker, file, fileEditor, changeList);
  }

  private ChangelistConflictNotificationPanel(ChangelistConflictTracker tracker, @NotNull VirtualFile file, @NotNull FileEditor fileEditor, LocalChangeList changeList) {
    super(fileEditor);

    myTracker = tracker;
    myFile = file;
    final ChangeListManager manager = tracker.getChangeListManager();
    myChangeList = changeList;
    myLabel.setText(VcsBundle.message("changes.file.from.non.active.changelist.is.modified"));
    createActionLabel(VcsBundle.message("link.label.move.changes"), () -> ChangelistConflictResolution.MOVE.resolveConflict(myTracker.getProject(), myChangeList.getChanges(), myFile)).
      setToolTipText(VcsBundle.message("changes.move.changes.to.active.change.list.name", manager.getDefaultChangeList().getName()));

    createActionLabel(VcsBundle.message("link.label.switch.changelist"), () -> {
      Change change = myTracker.getChangeListManager().getChange(myFile);
      if (change == null) {
        Messages.showInfoMessage(VcsBundle.message("dialog.message.no.changes.for.this.file"), VcsBundle.message("dialog.title.message"));
      }
      else {
        ChangelistConflictResolution.SWITCH.resolveConflict(myTracker.getProject(), Collections.singletonList(change), null);
      }
    }).setToolTipText(VcsBundle.message("changes.set.active.changelist.to.change.list.name", myChangeList.getName()));

    createActionLabel(VcsBundle.message("link.label.ignore"), () -> myTracker.ignoreConflict(myFile, true)).setToolTipText(
      VcsBundle.message("changes.hide.this.notification"));

    myLinksPanel.add(new InplaceButton(VcsBundle.message("tooltip.show.options.dialog"), AllIcons.General.Settings, new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {

        ShowSettingsUtil.getInstance().editConfigurable(myTracker.getProject(), new ChangelistConflictConfigurable(tracker));
      }
    }));
  }
}
