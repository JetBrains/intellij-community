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

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;

public class FileInfo
{
    public FileInfo(PsiFile file)
    {
        this.file = file;
    }

    public String getPathName()
    {
        return getVirtualFile() != null ? getVirtualFile().getPath() : "";
    }

    public String getFileName()
    {
        return getVirtualFile() != null ? getVirtualFile().getName() : "";
    }

    public String getClassName()
    {
        return getFileName();
    }

    public String getQualifiedClassName()
    {
       return getPathName();
    }

    public DateInfo getLastModified()
    {
        if (getVirtualFile() != null)
        {
            return new DateInfo(getVirtualFile().getTimeStamp());
        }
        else
        {
            return new DateInfo();
        }
    }

    private VirtualFile getVirtualFile()
    {
        return file.getVirtualFile();
    }

    private final PsiFile file;
}