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
package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsBundle;

public class DeleteAlreadyUnshelvedAction extends AnAction {
  private final String myText;

  public DeleteAlreadyUnshelvedAction() {
    myText = VcsBundle.message("delete.all.already.unshelved");
  }

  @Override
  public void update(final AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    final Presentation presentation = e.getPresentation();
    if (project == null) {
      presentation.setEnabled(false);
      presentation.setVisible(false);
    }
    presentation.setEnabled(true);
    presentation.setVisible(true);
    presentation.setText(myText);
  }

  public void actionPerformed(final AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      return;
    }
    final int result = Messages
      .showYesNoDialog(project, VcsBundle.message("delete.all.already.unshelved.confirmation"), myText,
                       Messages.getWarningIcon());
    if (Messages.YES == result) {
      final ShelveChangesManager manager = ShelveChangesManager.getInstance(project);
      manager.clearRecycled();
    }
  }
}
