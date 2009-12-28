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
package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.CurrentContentRevision;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesBrowserUseCase;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class ShowDiffWithLocalAction extends AnAction implements DumbAware {
  public ShowDiffWithLocalAction() {          
    super(VcsBundle.message("show.diff.with.local.action.text"),
          VcsBundle.message("show.diff.with.local.action.description"),
          IconLoader.getIcon("/actions/diffWithCurrent.png"));
  }

  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(PlatformDataKeys.PROJECT);
    Change[] changes = e.getData(VcsDataKeys.CHANGES);
    assert changes != null;
    List<Change> changesToLocal = new ArrayList<Change>();
    for(Change change: changes) {
      ContentRevision afterRevision = change.getAfterRevision();
      if (afterRevision != null && isValidAfterRevision(afterRevision)) {
        changesToLocal.add(new Change(afterRevision, CurrentContentRevision.create(afterRevision.getFile())));
      }
    }
    if (!changesToLocal.isEmpty()) {
      Change[] changeArray = changesToLocal.toArray(new Change[changesToLocal.size()]);
      ShowDiffAction.showDiffForChange(changeArray, 0, project);
    }
  }

  public void update(final AnActionEvent e) {
    Project project = e.getData(PlatformDataKeys.PROJECT);
    Change[] changes = e.getData(VcsDataKeys.CHANGES);

    e.getPresentation().setEnabled(project != null && changes != null &&
                                   (! CommittedChangesBrowserUseCase.IN_AIR.equals(CommittedChangesBrowserUseCase.DATA_KEY.getData(e.getDataContext()))) &&
                                   anyHasAfterRevision(changes));
  }

  private static boolean isValidAfterRevision(final ContentRevision afterRevision) {
    return afterRevision != null && !afterRevision.getFile().isNonLocal() && !afterRevision.getFile().isDirectory();
  }

  private static boolean anyHasAfterRevision(final Change[] changes) {
    for(Change c: changes) {
      if (isValidAfterRevision(c.getAfterRevision())) {
        return true;
      }
    }
    return false;
  }
}
