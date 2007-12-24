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

import com.intellij.lang.Language;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.maddyhome.idea.copyright.options.Options;
import com.maddyhome.idea.copyright.util.FileTypeUtil;

public class UpdateCopyrightFactory
{
    public static UpdateCopyright createUpdateCopyright(Project project, Module module, PsiFile file,
        Options options)
    {
        return createUpdateCopyright(project, module, file.getVirtualFile(), file.getFileType(), options);
    }

    public static UpdateCopyright createUpdateCopyright(Project project, Module module, VirtualFile file,
        Options options)
    {
        return createUpdateCopyright(project, module, file, file.getFileType(), options);
    }

    private static UpdateCopyright createUpdateCopyright(Project project, Module module, VirtualFile file,
        FileType base, Options options)
    {
        if (base == null || file == null)
        {
            return null;
        }

        // NOTE - any changes here require changes to LanguageOptionsFactory and ConfigTabFactory
        FileType type = FileTypeUtil.getInstance().getFileTypeByType(base);
        logger.debug("file=" + file);
        logger.debug("type=" + type.getName());

        if (type.equals(StdFileTypes.JAVA))
        {
            return new UpdateJavaFileCopyright(project, module, file, options);
        }
        else if (type.equals(StdFileTypes.XML))
        {
            return new UpdateXmlFileCopyright(project, module, file, options);
        }
        else if (type.equals(StdFileTypes.HTML))
        {
            return new UpdateXmlFileCopyright(project, module, file, options);
        }
        else if (type.equals(StdFileTypes.JSP))
        {
            return new UpdateJspFileCopyright(project, module, file, options);
        }
        else if (type.equals(StdFileTypes.PROPERTIES))
        {
            return new UpdatePropertiesFileCopyright(project, module, file, options);
        }
        else if ("JavaScript".equals(type.getName()))
        {
            return new UpdateJavaScriptFileCopyright(project, module, file, options);
        }
        else
        {
            if (type instanceof LanguageFileType)
            {
                Language lang = ((LanguageFileType)type).getLanguage();
                if (lang.equals(StdLanguages.CSS))
                {
                    return new UpdateCssFileCopyright(project, module, file, options);
                }
            }
        }

        logger.info("oops");
        return null;
    }

    private UpdateCopyrightFactory()
    {
    }

    private static Logger logger = Logger.getInstance(UpdateCopyrightFactory.class.getName());
}