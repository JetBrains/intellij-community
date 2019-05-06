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

package com.maddyhome.idea.copyright.pattern;

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