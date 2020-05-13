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

package com.maddyhome.idea.copyright.psi;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.maddyhome.idea.copyright.CopyrightProfile;
import com.maddyhome.idea.copyright.CopyrightUpdaters;
import org.jetbrains.annotations.Nullable;

public class UpdateCopyrightFactory
{
    @Nullable
    public static UpdateCopyright createUpdateCopyright(Project project, Module module, PsiFile file,
        CopyrightProfile options)
    {
      final VirtualFile virtualFile = file.getVirtualFile();
      if (virtualFile == null) return null;
      return createUpdateCopyright(project, module, virtualFile, virtualFile.getFileType(), options);
    }



    private static UpdateCopyright createUpdateCopyright(Project project, Module module, VirtualFile file,
        FileType type, CopyrightProfile options)
    {
        // NOTE - any changes here require changes to LanguageOptionsFactory and ConfigTabFactory
        logger.debug("file=" + file);
        logger.debug("type=" + type.getName());

        return CopyrightUpdaters.INSTANCE.forFileType(type).createInstance(project, module, file, type, options);
    }

    private UpdateCopyrightFactory()
    {
    }

    private static final Logger logger = Logger.getInstance(UpdateCopyrightFactory.class.getName());
}