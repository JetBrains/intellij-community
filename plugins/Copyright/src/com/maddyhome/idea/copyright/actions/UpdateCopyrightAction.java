/*
 * Copyright 2000-2008 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.maddyhome.idea.copyright.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.maddyhome.idea.copyright.pattern.FileUtil;
import com.maddyhome.idea.copyright.util.FileTypeUtil;

public class UpdateCopyrightAction extends AnAction {
  public void update(AnActionEvent event) {
    final Presentation presentation = event.getPresentation();
    final DataContext context = event.getDataContext();
    final Project project = PlatformDataKeys.PROJECT.getData(context);
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }

    final VirtualFile[] files = PlatformDataKeys.VIRTUAL_FILE_ARRAY.getData(context);
    final Editor editor = PlatformDataKeys.EDITOR.getData(context);
    if (editor != null) {
      final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      if (file == null || !FileTypeUtil.getInstance().isSupportedFile(file)) {
        presentation.setEnabled(false);
        return;
      }
    }
    else if (files != null && FileUtil.areFiles(files)) {
      boolean copyrightEnabled  = false;
      for (VirtualFile vfile : files) {
        if (FileTypeUtil.getInstance().isSupportedFile(vfile)) {
          copyrightEnabled = true;
          break;
        }
      }
      if (!copyrightEnabled) {
        presentation.setEnabled(false);
        return;
      }

    }
    else if ((files == null || files.length != 1) &&
             LangDataKeys.MODULE_CONTEXT.getData(context) == null &&
             PlatformDataKeys.PROJECT_CONTEXT.getData(context) == null) {
      final PsiElement elem = LangDataKeys.PSI_ELEMENT.getData(context);
      if (elem == null) {
        presentation.setEnabled(false);
        return;
      }

      if (!(elem instanceof PsiDirectory)) {
        final PsiFile file = elem.getContainingFile();
        if (file == null || !FileTypeUtil.getInstance().isSupportedFile(file.getVirtualFile())) {
          presentation.setEnabled(false);
          return;
        }
      }
    }
    presentation.setEnabled(true);
  }

  public void actionPerformed(AnActionEvent event) {
    final DataContext context = event.getDataContext();
    final Project project = PlatformDataKeys.PROJECT.getData(context);
    assert project != null;

    final Module module = LangDataKeys.MODULE.getData(context);
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    final VirtualFile[] files = PlatformDataKeys.VIRTUAL_FILE_ARRAY.getData(context);
    final Editor editor = PlatformDataKeys.EDITOR.getData(context);

    PsiFile file = null;
    PsiDirectory dir;
    if (editor != null) {
      file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      if (file == null) {
        return;
      }
      dir = file.getContainingDirectory();
    }
    else {
      if (FileUtil.areFiles(files)) {
        new UpdateCopyrightProcessor(project, null, FileUtil.convertToPsiFiles(files, project)).run();
        return;
      }
      final Module modCtx = LangDataKeys.MODULE_CONTEXT.getData(context);
      if (modCtx != null) {
        if (Messages.showOkCancelDialog(project, "Update copyright for module \'" + modCtx.getName() + "\'?", "Update Copyright", Messages.getQuestionIcon()) != DialogWrapper.OK_EXIT_CODE) return;
        new UpdateCopyrightProcessor(project, module).run();
        return;
      }

      final PsiElement psielement = LangDataKeys.PSI_ELEMENT.getData(context);
      if (psielement == null) {
        return;
      }

      if (psielement instanceof PsiPackage) {
        dir = ((PsiPackage)psielement).getDirectories()[0];
      }
      else if (psielement instanceof PsiDirectory) {
        dir = (PsiDirectory)psielement;
      }
      else {
        file = psielement.getContainingFile();
        if (file == null) {
          return;
        }
        dir = file.getContainingDirectory();
      }
    }

    final RecursionDlg recDlg = new RecursionDlg(project, file != null ? file.getVirtualFile() : dir.getVirtualFile());
    recDlg.show();
    if (!recDlg.isOK()) {
      return;
    }

    if (recDlg.isAll()) {
      new UpdateCopyrightProcessor(project, module, dir, recDlg.includeSubdirs()).run();
    }
    else {
      new UpdateCopyrightProcessor(project, module, file).run();
    }
  }

}