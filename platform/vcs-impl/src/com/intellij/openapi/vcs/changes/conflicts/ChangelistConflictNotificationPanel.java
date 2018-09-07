// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.conflicts;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.InplaceButton;
import org.jetbrains.annotations.Nullable;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collections;

/**
* @author Dmitry Avdeev
*/
public class ChangelistConflictNotificationPanel extends EditorNotificationPanel {

  private final ChangeList myChangeList;
  private final VirtualFile myFile;
  private final ChangelistConflictTracker myTracker;

  @Nullable
  public static ChangelistConflictNotificationPanel create(ChangelistConflictTracker tracker, VirtualFile file) {
    final ChangeListManager manager = tracker.getChangeListManager();
    final Change change = manager.getChange(file);
    if (change == null) return null;
    final LocalChangeList changeList = manager.getChangeList(change);
    if (changeList == null) return null;
    return new ChangelistConflictNotificationPanel(tracker, file, changeList);
  }

  private ChangelistConflictNotificationPanel(ChangelistConflictTracker tracker, VirtualFile file, LocalChangeList changeList) {
    myTracker = tracker;
    myFile = file;
    final ChangeListManager manager = tracker.getChangeListManager();
    myChangeList = changeList;
    myLabel.setText("File from non-active changelist is modified");
    createActionLabel("Move changes", () -> ChangelistConflictResolution.MOVE.resolveConflict(myTracker.getProject(), myChangeList.getChanges(), myFile)).
      setToolTipText("Move changes to active changelist (" + manager.getDefaultChangeList().getName() + ")");

    createActionLabel("Switch changelist", () -> {
      Change change = myTracker.getChangeListManager().getChange(myFile);
      if (change == null) {
        Messages.showInfoMessage("No changes for this file", "Message");
      }
      else {
        ChangelistConflictResolution.SWITCH.resolveConflict(myTracker.getProject(), Collections.singletonList(change), null);
      }
    }).setToolTipText("Set active changelist to '" + myChangeList.getName() + "'");

    createActionLabel("Ignore", () -> myTracker.ignoreConflict(myFile, true)).setToolTipText("Hide this notification");

    myLinksPanel.add(new InplaceButton("Show options dialog", AllIcons.General.Settings, new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {

        ShowSettingsUtil.getInstance().editConfigurable(myTracker.getProject(),
                                                        new ChangelistConflictConfigurable((ChangeListManagerImpl)manager));
      }
    }));
  }
}
