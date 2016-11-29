/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesBrowserUseCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.openapi.vcs.changes.actions.diff.ShowDiffAction.showDiffForChange;

/**
 * @author yole
 */
public class ShowDiffWithLocalAction extends AnAction implements DumbAware {
  public ShowDiffWithLocalAction() {
    super(VcsBundle.message("show.diff.with.local.action.text"),
          VcsBundle.message("show.diff.with.local.action.description"),
          AllIcons.Actions.DiffWithCurrent);
  }

  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (ChangeListManager.getInstance(project).isFreezedWithNotification(null)) return;
    ChangesSelection selection = e.getRequiredData(VcsDataKeys.CHANGES_SELECTION);

    int index = 0;
    List<Change> changesToLocal = new ArrayList<>();
    for (int i = 0; i < selection.getChanges().size(); i++) {
      if (i == selection.getIndex()) index = changesToLocal.size();
      ContentRevision afterRevision = selection.getChanges().get(i).getAfterRevision();
      if (afterRevision != null && isValidAfterRevision(afterRevision)) {
        changesToLocal.add(new Change(afterRevision, CurrentContentRevision.create(afterRevision.getFile())));
      }
    }
    if (!changesToLocal.isEmpty()) {
      showDiffForChange(project, changesToLocal, index);
    }
  }

  public void update(final AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    ChangesSelection selection = e.getData(VcsDataKeys.CHANGES_SELECTION);
    boolean isInAir = CommittedChangesBrowserUseCase.IN_AIR.equals(CommittedChangesBrowserUseCase.DATA_KEY.getData(e.getDataContext()));

    e.getPresentation().setEnabled(project != null && selection != null && !isInAir && anyHasAfterRevision(selection.getChanges()));
  }

  private static boolean isValidAfterRevision(@Nullable final ContentRevision afterRevision) {
    return afterRevision != null && !afterRevision.getFile().isNonLocal() && !afterRevision.getFile().isDirectory();
  }

  private static boolean anyHasAfterRevision(@NotNull final List<Change> changes) {
    for (Change c : changes) {
      if (isValidAfterRevision(c.getAfterRevision())) {
        return true;
      }
    }
    return false;
  }
}
