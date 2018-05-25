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
package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsBundle;

public class RestoreShelvedChange extends DumbAwareAction {
  public RestoreShelvedChange() {
    super("Restore");
  }

  @Override
  public void update(final AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    final ShelvedChangeList[] recycledChanges = e.getData(ShelvedChangesViewManager.SHELVED_RECYCLED_CHANGELIST_KEY);
    e.getPresentation().setText(VcsBundle.message("vcs.shelf.action.restore.text"));
    e.getPresentation().setDescription(VcsBundle.message("vcs.shelf.action.restore.description"));
    e.getPresentation().setEnabled((project != null) && ((recycledChanges != null) && (recycledChanges.length == 1)));
  }

  public void actionPerformed(final AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    final ShelvedChangeList[] recycledChanges = e.getData(ShelvedChangesViewManager.SHELVED_RECYCLED_CHANGELIST_KEY);
    if (recycledChanges != null && recycledChanges.length == 1) {
      ShelveChangesManager.getInstance(project).restoreList(recycledChanges[0]);
    }
  }
}
