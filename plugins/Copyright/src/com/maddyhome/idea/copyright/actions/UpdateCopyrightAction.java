/*
 *  Copyright 2000-2007 JetBrains s.r.o.
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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.maddyhome.idea.copyright.pattern.FileUtil;
import com.maddyhome.idea.copyright.util.FileTypeUtil;

public class UpdateCopyrightAction extends AnAction
{
    public void update(AnActionEvent event)
    {
        Presentation presentation = event.getPresentation();
        DataContext context = event.getDataContext();
        Project project = (Project)context.getData(DataConstants.PROJECT);
        if (project == null)
        {
            presentation.setEnabled(false);
            return;
        }

        VirtualFile[] files = (VirtualFile[])context.getData(DataConstants.VIRTUAL_FILE_ARRAY);
        Editor editor = (Editor)context.getData(DataConstants.EDITOR);
        if (editor != null)
        {
            PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
            if (file == null || !FileTypeUtil.getInstance().isSupportedFile(file))
            {
                presentation.setEnabled(false);
            }
        }
        else if (files != null && FileUtil.areFiles(files))
        {
            for (VirtualFile vfile : files)
            {
                PsiFile file = PsiManager.getInstance(project).findFile(vfile);
                if (file == null || !FileTypeUtil.getInstance().isSupportedFile(file.getVirtualFile()))
                {
                    presentation.setEnabled(false);
                    return;
                }
            }

            presentation.setEnabled(true);

        }
        else if ((files == null || files.length != 1) && context.getData(DataConstants.MODULE_CONTEXT) == null &&
            context.getData(DataConstants.PROJECT_CONTEXT) == null)
        {
            PsiElement elem = (PsiElement)context.getData(DataConstants.PSI_ELEMENT);
            if (elem == null)
            {
                presentation.setEnabled(false);
                return;
            }

            if (!(elem instanceof PsiDirectory))
            {
                PsiFile file = elem.getContainingFile();
                if (file == null || !FileTypeUtil.getInstance().isSupportedFile(file.getVirtualFile()))
                {
                    presentation.setEnabled(false);

                }
            }
        }
    }

    public void actionPerformed(AnActionEvent event)
    {
        DataContext context = event.getDataContext();
        Project project = (Project)context.getData(DataConstants.PROJECT);
        Module module = (Module)context.getData(DataConstants.MODULE);
        PsiDocumentManager.getInstance(project).commitAllDocuments();

        VirtualFile[] files = (VirtualFile[])context.getData(DataConstants.VIRTUAL_FILE_ARRAY);
        Editor editor = (Editor)context.getData(DataConstants.EDITOR);

        PsiFile file = null;
        PsiDirectory dir;
        if (editor != null)
        {
            file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
            if (file == null)
            {
                return;
            }
            dir = file.getContainingDirectory();
        }
        else
        {
            if (FileUtil.areFiles(files))
            {
                (new UpdateCopyrightProcessor(project, null, FileUtil.convertToPsiFiles(files, project))).run();

                return;
            }
            Module modCtx = (Module)context.getData(DataConstants.MODULE_CONTEXT);
            if (modCtx != null)
            {
                ModuleDlg dlg = new ModuleDlg(project, module);
                dlg.show();
                if (!dlg.isOK())
                {
                    return;
                }

                (new UpdateCopyrightProcessor(project, module)).run();

                return;
            }

            PsiElement psielement = (PsiElement)context.getData(DataConstants.PSI_ELEMENT);
            if (psielement == null)
            {
                return;
            }

            if (psielement instanceof PsiPackage)
            {
                dir = ((PsiPackage)psielement).getDirectories()[0];
            }
            else if (psielement instanceof PsiDirectory)
            {
                dir = (PsiDirectory)psielement;
            }
            else
            {
                file = psielement.getContainingFile();
                if (file == null)
                {
                    return;
                }
                dir = file.getContainingDirectory();
            }
        }

        RecursionDlg recDlg = new RecursionDlg(project, file != null ? file.getVirtualFile() : dir.getVirtualFile());
        recDlg.show();
        if (!recDlg.isOK())
        {
            return;
        }

        if (recDlg.isAll())
        {
            (new UpdateCopyrightProcessor(project, module, dir, recDlg.includeSubdirs())).run();
        }

        else
        {
            (new UpdateCopyrightProcessor(project, module, file)).run();
        }
    }

}