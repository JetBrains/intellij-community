// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

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

public final class UpdateCopyrightFactory
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