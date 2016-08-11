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

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.actions.AbstractCommonCheckinAction;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.stream.Stream;

import static com.intellij.util.ObjectUtils.notNull;

public abstract class AbstractCommitChangesAction extends AbstractCommonCheckinAction {
  @NotNull
  @Override
  protected FilePath[] getRoots(@NotNull VcsContext context) {
    return getAllContentRoots(context);
  }

  @Override
  protected boolean approximatelyHasRoots(@NotNull VcsContext dataContext) {
    return ProjectLevelVcsManager.getInstance(dataContext.getProject()).hasAnyMappings();
  }

  @Override
  protected void update(@NotNull VcsContext vcsContext, @NotNull Presentation presentation) {
    super.update(vcsContext, presentation);

    if (presentation.isEnabledAndVisible()) {
      Change[] changes = vcsContext.getSelectedChanges();

      if (vcsContext.getPlace().equals(ActionPlaces.CHANGES_VIEW_POPUP)) {
        ChangeList[] changeLists = vcsContext.getSelectedChangeLists();

        presentation.setEnabled(!ArrayUtil.isEmpty(changeLists)
                                ? changeLists.length == 1 && !changeLists[0].getChanges().isEmpty()
                                : !ArrayUtil.isEmpty(changes));
      }

      if (presentation.isEnabled() && !ArrayUtil.isEmpty(changes)) {
        disableIfAnyHijackedChanges(notNull(vcsContext.getProject()), presentation, changes);
      }
    }
  }

  private static void disableIfAnyHijackedChanges(@NotNull Project project, @NotNull Presentation presentation, @NotNull Change[] changes) {
    ChangeListManager manager = ChangeListManager.getInstance(project);
    boolean hasHijackedChanges =
      Stream.of(changes).anyMatch(change -> change.getFileStatus() == FileStatus.HIJACKED && manager.getChangeList(change) == null);

    presentation.setEnabled(!hasHijackedChanges);
  }
}