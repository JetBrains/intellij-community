package com.maddyhome.idea.copyright.util;

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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;

public class FileUtil
{
    public static boolean areFiles(VirtualFile[] files)
    {
        if (files == null || files.length < 2)
        {
            return false;
        }

        for (VirtualFile file : files)
        {
            if (file.isDirectory())
            {
                return false;
            }
        }

        return true;
    }

    public static PsiFile[] convertToPsiFiles(VirtualFile[] files, Project project)
    {
        PsiFile[] res = new PsiFile[files.length];
        PsiManager manager = PsiManager.getInstance(project);
        for (int i = 0; i < files.length; i++)
        {
            res[i] = manager.findFile(files[i]);
        }

        return res;
    }

    private FileUtil()
    {
    }
}