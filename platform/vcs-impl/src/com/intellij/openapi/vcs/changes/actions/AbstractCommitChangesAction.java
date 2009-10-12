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

/**
 * @author yole
 */
public abstract class AbstractCommitChangesAction extends AbstractCommonCheckinAction {
  protected FilePath[] getRoots(VcsContext context) {
    return getAllContentRoots(context);
  }

  @Override
  protected boolean approximatelyHasRoots(VcsContext dataContext) {
    final Project project = dataContext.getProject();
    final ProjectLevelVcsManager manager = ProjectLevelVcsManager.getInstance(project);
    return manager.hasAnyMappings();
  }

  protected boolean filterRootsBeforeAction() {
    return false;
  }

  @Override
  protected void update(final VcsContext vcsContext, final Presentation presentation) {
    super.update(vcsContext, presentation);
    if (presentation.isVisible() && presentation.isEnabled()) {
      final ChangeList[] selectedChangeLists = vcsContext.getSelectedChangeLists();
      final Change[] selectedChanges = vcsContext.getSelectedChanges();
      if (vcsContext.getPlace().equals(ActionPlaces.CHANGES_VIEW_POPUP)) {
        if (selectedChangeLists != null && selectedChangeLists.length > 0) {
          presentation.setEnabled(selectedChangeLists.length == 1);
        }
        else {
          presentation.setEnabled (selectedChanges != null && selectedChanges.length > 0);
        }
      }
      if (presentation.isEnabled() && selectedChanges != null) {
        final ChangeListManager changeListManager = ChangeListManager.getInstance(vcsContext.getProject());
        for(Change c: selectedChanges) {
          if (c.getFileStatus() == FileStatus.HIJACKED && changeListManager.getChangeList(c) == null) {
            presentation.setEnabled(false);
            break;
          }
        }
      }
    }
  }
}