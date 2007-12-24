package com.maddyhome.idea.copyright.psi;

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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.maddyhome.idea.copyright.options.Options;

public class UpdatePropertiesFileCopyright extends UpdatePsiFileCopyright
{
    public UpdatePropertiesFileCopyright(Project project, Module module, VirtualFile root, Options options)
    {
        super(project, module, root, options);
    }

    protected void scanFile()
    {
        PsiElement first = getFile().getFirstChild(); // PropertiesList
        if (first != null)
        {
            first = first.getFirstChild(); // First member of properties list
        }
        PsiElement last = first;
        PsiElement next = first;
        while (next != null)
        {
            if (next instanceof PsiComment || next instanceof PsiWhiteSpace)
            {
                next = getNextSibling(next);
            }
            else
            {
                break;
            }
            last = next;
        }

        if (first != null)
        {
            checkComments(first, last, true);
        }
        else
        {
            first = getFile().getFirstChild();
            checkComments(first, first, true);
        }
    }
}
