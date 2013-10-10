/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.vcs.changes.patch.ApplyPatchDefaultExecutor;
import com.intellij.openapi.vcs.changes.patch.ApplyPatchDifferentiatedDialog;
import com.intellij.openapi.vcs.changes.patch.ApplyPatchExecutor;
import com.intellij.openapi.vcs.changes.patch.ApplyPatchMode;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;
import java.util.Collections;

/**
 * @author irengrig
 *         Date: 2/25/11
 *         Time: 5:50 PM
 */
public class UnshelveWithDialogAction extends AnAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    final ShelvedChangeList[] changeLists = e.getData(ShelvedChangesViewManager.SHELVED_CHANGELIST_KEY);
    if (project == null || changeLists == null || changeLists.length != 1) return;

    FileDocumentManager.getInstance().saveAllDocuments();

    final VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(changeLists[0].PATH));
    if (virtualFile == null) {
      VcsBalloonProblemNotifier.showOverChangesView(project, "Can not find path file", MessageType.ERROR);
      return;
    }
    if (! changeLists[0].getBinaryFiles().isEmpty()) {
      VcsBalloonProblemNotifier.showOverChangesView(project, "Binary file(s) would be skipped.", MessageType.WARNING);
    }
    final ApplyPatchDifferentiatedDialog dialog =
      new ApplyPatchDifferentiatedDialog(project, new ApplyPatchDefaultExecutor(project), Collections.<ApplyPatchExecutor>emptyList(),
                                         ApplyPatchMode.UNSHELVE, virtualFile);
    dialog.setHelpId("reference.dialogs.vcs.unshelve");
    dialog.show();
  }

  @Override
  public void update(AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    final ShelvedChangeList[] changes = e.getData(ShelvedChangesViewManager.SHELVED_CHANGELIST_KEY);
    e.getPresentation().setEnabled(project != null && changes != null && changes.length == 1);
  }
}
