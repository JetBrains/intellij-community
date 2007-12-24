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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaFile;
import com.maddyhome.idea.copyright.options.JavaOptions;
import com.maddyhome.idea.copyright.options.Options;

public class UpdateJavaFileCopyright extends UpdatePsiFileCopyright
{
    public UpdateJavaFileCopyright(Project project, Module module, VirtualFile root, Options options)
    {
        super(project, module, root, options);
    }

    protected boolean accept()
    {
        return getFile() instanceof PsiJavaFile;
    }

    protected void scanFile()
    {
        logger.debug("updating " + getFile().getVirtualFile());

        PsiJavaFile javaFile = (PsiJavaFile)getFile();
        PsiElement pkg = javaFile.getPackageStatement();
        PsiElement imports = javaFile.getImportList();
        PsiElement topclass = null;
        PsiElement[] classes = javaFile.getClasses();
        if (classes.length > 0)
        {
            topclass = classes[0];
        }

        PsiElement first = javaFile.getFirstChild();

        int location = getLanguageOptions().getFileLocation();
        if (pkg != null)
        {
            checkComments(first, pkg, location == JavaOptions.LOCATION_BEFORE_PACKAGE);
            first = pkg;
        }
        else if (location == JavaOptions.LOCATION_BEFORE_PACKAGE)
        {
            location = JavaOptions.LOCATION_BEFORE_IMPORT;
        }

        if (imports != null && imports.getChildren().length > 0)
        {
            checkComments(first, imports, location == JavaOptions.LOCATION_BEFORE_IMPORT);
            first = imports;
        }
        else if (location == JavaOptions.LOCATION_BEFORE_IMPORT)
        {
            location = JavaOptions.LOCATION_BEFORE_CLASS;
        }

        if (topclass != null)
        {
            checkComments(first, topclass, location == JavaOptions.LOCATION_BEFORE_CLASS);
        }
        else if (location == JavaOptions.LOCATION_BEFORE_CLASS)
        {
            // no package, no imports, no top level class
        }
    }

    private static Logger logger = Logger.getInstance(UpdateJavaFileCopyright.class.getName());
}