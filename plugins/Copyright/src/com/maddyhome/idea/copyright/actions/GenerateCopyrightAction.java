package com.maddyhome.idea.copyright.actions;

/*
 * Copyright - Copyright notice updater for IDEA
 * Copyright (C) 2004-2005 Rick Maddy. All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.maddyhome.idea.copyright.util.FileTypeUtil;

public class GenerateCopyrightAction extends AnAction
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

        PsiFile file = DataKeys.PSI_FILE.getData(context);
        if (file == null) {
            Editor editor = (Editor)context.getData(DataConstants.EDITOR);
            if (editor != null)
            {
                file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
            }
        }
        if (file == null || !FileTypeUtil.getInstance().isSupportedFile(file))
        {
            presentation.setEnabled(false);
        }
    }

    public void actionPerformed(AnActionEvent event)
    {
        DataContext context = event.getDataContext();
        Project project = (Project)context.getData(DataConstants.PROJECT);
        Module module = (Module)context.getData(DataConstants.MODULE);
        PsiDocumentManager.getInstance(project).commitAllDocuments();

        Editor editor = (Editor)context.getData(DataConstants.EDITOR);

        PsiFile file = null;
        if (editor != null)
        {
            file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
            if (file == null)
            {
                return;
            }
        }

        (new UpdateCopyrightProcessor(project, module, file)).run();
    }
}