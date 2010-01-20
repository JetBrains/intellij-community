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

import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.InplaceButton;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collections;
import java.util.List;

/**
* @author Dmitry Avdeev
*/
public class ChangelistConflictNotificationPanel extends EditorNotificationPanel {

  private final ChangeList myChangeList;
  private final Change myChange;
  private final VirtualFile myFile;
  private ChangelistConflictTracker myTracker;

  public ChangelistConflictNotificationPanel(ChangelistConflictTracker tracker, VirtualFile file) {

    myTracker = tracker;
    myFile = file;
    final ChangeListManager manager = tracker.getChangeListManager();
    myChange = manager.getChange(file);
    myChangeList = manager.getChangeList(myChange);
    assert myChangeList != null;
    myLabel.setText("File from non-active changelist is modified");
    createActionLabel("Move changes", new Runnable() {
      public void run() {
        ChangelistConflictResolution.MOVE.resolveConflict(myTracker.getProject(), myChangeList.getChanges());
      }
    }).setToolTipText("Move changes to active changelist (" + manager.getDefaultChangeList().getName() + ")");

    createActionLabel("Switch changelist", new Runnable() {
      public void run() {
        List<Change> changes = Collections.singletonList(myTracker.getChangeListManager().getChange(myFile));
        ChangelistConflictResolution.SWITCH.resolveConflict(myTracker.getProject(), changes);
      }
    }).setToolTipText("Set active changelist to '" + myChangeList.getName() + "'");

    createActionLabel("Ignore", new Runnable() {
      public void run() {
        myTracker.ignoreConflict(myFile, true);
      }
    }).setToolTipText("Hide this notification");

//    setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));

    myLinksPanel.add(new InplaceButton("Show options dialog", IconLoader.getIcon("/general/ideOptions.png"), new ActionListener() {
      public void actionPerformed(ActionEvent e) {

        ShowSettingsUtil.getInstance().editConfigurable(myTracker.getProject(),
                                                        new ChangelistConflictConfigurable((ChangeListManagerImpl)manager));
      }
    }));
  }
}
