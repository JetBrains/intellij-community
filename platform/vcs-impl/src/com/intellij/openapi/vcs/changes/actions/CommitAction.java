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

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 02.11.2006
 * Time: 21:53:06
 */
package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.ui.CommitChangeListDialog;

import java.util.Arrays;
import java.util.List;

public class CommitAction extends AnAction implements DumbAware {
  public void update(AnActionEvent e) {
    Project project = e.getData(PlatformDataKeys.PROJECT);
    boolean enabled = false;
    if (project != null) {
      if (ProjectLevelVcsManager.getInstance(project).isBackgroundVcsOperationRunning()) {
        e.getPresentation().setEnabled(false);
        return;
      }
      Change[] changes = e.getData(VcsDataKeys.CHANGES);

      if (changes != null && ChangesUtil.allChangesInOneList(project, changes)) {
        for(Change c: changes) {
          final AbstractVcs vcs = ChangesUtil.getVcsForChange(c, project);
          if (vcs != null && vcs.getCheckinEnvironment() != null) {
            enabled = true;
            break;
          }
        }
      }
    }
    e.getPresentation().setEnabled(enabled);
  }

  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project == null) return;
    if (ProjectLevelVcsManager.getInstance(project).isBackgroundVcsOperationRunning()) {
      return;
    }
    
    final Change[] changes = e.getData(VcsDataKeys.CHANGES);
    final ChangeList list = ChangesUtil.getChangeListIfOnlyOne(project, changes);
    if ((list == null) || (! (list instanceof LocalChangeList))) return;

    ChangeListManager.getInstance(project).invokeAfterUpdate(new Runnable() {
      public void run() {
        final List<Change> changesList = Arrays.asList(changes);
        final List<CommitExecutor> executors = CommitChangeListDialog.collectExecutors(project, changesList);
        CommitChangeListDialog.commitChanges(project, changesList, (LocalChangeList) list,
                                             executors, true, null);
      }
    }, InvokeAfterUpdateMode.SYNCHRONOUS_CANCELLABLE, VcsBundle.message("waiting.changelists.update.for.show.commit.dialog.message"), null);
  }
}