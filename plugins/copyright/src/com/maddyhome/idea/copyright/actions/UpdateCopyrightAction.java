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

package com.maddyhome.idea.copyright.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtilCore;
import com.maddyhome.idea.copyright.CopyrightManager;
import com.maddyhome.idea.copyright.pattern.FileUtil;
import com.maddyhome.idea.copyright.util.FileTypeUtil;

import java.util.ArrayList;
import java.util.List;

public class UpdateCopyrightAction extends AnAction {
  public void update(AnActionEvent event) {
    final boolean enabled = isEnabled(event);
    event.getPresentation().setEnabled(enabled);
    if (ActionPlaces.isPopupPlace(event.getPlace())) {
      event.getPresentation().setVisible(enabled);
    }
  }

  private static boolean isEnabled(AnActionEvent event) {
    final DataContext context = event.getDataContext();
    final Project project = CommonDataKeys.PROJECT.getData(context);
    if (project == null) {
      return false;
    }

    if (!CopyrightManager.getInstance(project).hasAnyCopyrights()) {
      return false;
    }
    final VirtualFile[] files = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(context);
    final Editor editor = CommonDataKeys.EDITOR.getData(context);
    if (editor != null) {
      final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      if (file == null || !FileTypeUtil.isSupportedFile(file)) {
        return false;
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
        return false;
      }

    }
    else if ((files == null || files.length != 1) &&
             LangDataKeys.MODULE_CONTEXT.getData(context) == null &&
             LangDataKeys.MODULE_CONTEXT_ARRAY.getData(context) == null &&
             PlatformDataKeys.PROJECT_CONTEXT.getData(context) == null) {
      final PsiElement[] elems = LangDataKeys.PSI_ELEMENT_ARRAY.getData(context);
      if (elems != null) {
        boolean copyrightEnabled = false;
        for (PsiElement elem : elems) {
          if (!(elem instanceof PsiDirectory)) {
            final PsiFile file = elem.getContainingFile();
            if (file == null || !FileTypeUtil.getInstance().isSupportedFile(file.getVirtualFile())) {
              copyrightEnabled = true;
              break;
            }
          }
        }
        if (!copyrightEnabled){
          return false;
        }
      }
    }
    return true;
  }

  public void actionPerformed(AnActionEvent event) {
    final DataContext context = event.getDataContext();
    final Project project = CommonDataKeys.PROJECT.getData(context);
    assert project != null;

    final Module module = LangDataKeys.MODULE.getData(context);
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    final VirtualFile[] files = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(context);
    final Editor editor = CommonDataKeys.EDITOR.getData(context);

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
        new UpdateCopyrightProcessor(project, module).run();
        return;
      }

      final Module[] modules = LangDataKeys.MODULE_CONTEXT_ARRAY.getData(context);
      if (modules != null && modules.length > 0) {
        List<PsiFile> psiFiles = new ArrayList<PsiFile>();
        for (Module mod : modules) {
          AbstractFileProcessor.findFiles(mod, psiFiles);
        }
        new UpdateCopyrightProcessor(project, null, PsiUtilCore.toPsiFileArray(psiFiles)).run();
        return;
      }

      final PsiElement psielement = CommonDataKeys.PSI_ELEMENT.getData(context);
      if (psielement == null) {
        return;
      }

      if (psielement instanceof PsiDirectoryContainer) {
        dir = ((PsiDirectoryContainer)psielement).getDirectories()[0];
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