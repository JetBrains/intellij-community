/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ImportIntoShelfAction extends DumbAwareAction {
  public ImportIntoShelfAction() {
    super(VcsBundle.messagePointer("action.ImportIntoShelfAction.text"),
          VcsBundle.messagePointer("action.ImportIntoShelfAction.description"), null);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    e.getPresentation().setEnabled(project != null);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) return;
    final FileChooserDescriptor descriptor = new FileChooserDescriptor(true, true, false, false, false, true);
    FileChooser.chooseFiles(descriptor, project, null, files -> {
      //gatherPatchFiles
      final ProgressManager pm = ProgressManager.getInstance();
      final ShelveChangesManager shelveChangesManager = ShelveChangesManager.getInstance(project);

      final List<VirtualFile> patchTypeFiles = new ArrayList<>();
      final boolean filesFound = pm.runProcessWithProgressSynchronously(
        () -> patchTypeFiles.addAll(shelveChangesManager.gatherPatchFiles(files)),
        VcsBundle.message("looking.for.patch.files"), true, project);
      if (!filesFound || patchTypeFiles.isEmpty()) return;
      if (!patchTypeFiles.equals(files)) {
        final String message = patchTypeFiles.size() == 1 ? VcsBundle.message("shelve.import.one.patch.file.prompt", patchTypeFiles.get(0).getPath()) :
                               VcsBundle.message("shelve.import.patches.prompt", patchTypeFiles.size());
        final int toImport = Messages.showYesNoDialog(project, message, VcsBundle.message("import.patches"), Messages.getQuestionIcon());
        if (Messages.NO == toImport) return;
      }
      pm.runProcessWithProgressSynchronously(() -> {
        final List<VcsException> exceptions = new ArrayList<>();
        final List<ShelvedChangeList> lists =
          shelveChangesManager.importChangeLists(patchTypeFiles, e1 -> exceptions.add(e1));
        if (!lists.isEmpty()) {
          ShelvedChangesViewManager.getInstance(project).activateView(lists.get(lists.size() - 1));
        }
        if (!exceptions.isEmpty()) {
          AbstractVcsHelper.getInstance(project).showErrors(exceptions, VcsBundle.message("patch.import.to.shelf.tab"));
        }
        if (lists.isEmpty() && exceptions.isEmpty()) {
          VcsBalloonProblemNotifier.showOverChangesView(project, VcsBundle.message("patch.import.no.patches.found.warning"), MessageType.WARNING);
        }
      }, VcsBundle.message("import.patches.into.shelf"), true, project);
    });
  }
}
