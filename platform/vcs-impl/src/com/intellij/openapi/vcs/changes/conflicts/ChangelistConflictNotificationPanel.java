/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
    createActionLabel("Move changes", new Runnable() {
      public void run() {
        ChangelistConflictResolution.MOVE.resolveConflict(myTracker.getProject(), myChangeList.getChanges());
      }
    }).setToolTipText("Move changes to active changelist (" + manager.getDefaultChangeList().getName() + ")");

    createActionLabel("Switch changelist", new Runnable() {
      public void run() {
        Change change = myTracker.getChangeListManager().getChange(myFile);
        if (change == null) {
          Messages.showInfoMessage("No changes for this file", "Message");
        }
        else {
          ChangelistConflictResolution.SWITCH.resolveConflict(myTracker.getProject(), Collections.singletonList(change));
        }
      }
    }).setToolTipText("Set active changelist to '" + myChangeList.getName() + "'");

    createActionLabel("Ignore", new Runnable() {
      public void run() {
        myTracker.ignoreConflict(myFile, true);
      }
    }).setToolTipText("Hide this notification");

    myLinksPanel.add(new InplaceButton("Show options dialog", AllIcons.General.Settings, new ActionListener() {
      public void actionPerformed(ActionEvent e) {

        ShowSettingsUtil.getInstance().editConfigurable(myTracker.getProject(),
                                                        new ChangelistConflictConfigurable((ChangeListManagerImpl)manager));
      }
    }));
  }
}
