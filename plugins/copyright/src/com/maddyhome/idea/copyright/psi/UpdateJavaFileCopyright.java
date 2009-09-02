/*
 * Copyright 2000-2008 JetBrains s.r.o.
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

package com.maddyhome.idea.copyright.psi;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaFile;
import com.maddyhome.idea.copyright.CopyrightProfile;
import com.maddyhome.idea.copyright.options.JavaOptions;

import java.util.ArrayList;
import java.util.List;

public class UpdateJavaFileCopyright extends UpdatePsiFileCopyright
{
    public UpdateJavaFileCopyright(Project project, Module module, VirtualFile root, CopyrightProfile options)
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
        PsiClass topclass = null;
        PsiClass[] classes = javaFile.getClasses();
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
            final List<PsiComment> comments = new ArrayList<PsiComment>();
            collectComments(first, topclass, comments);
            collectComments(topclass.getFirstChild(), topclass.getModifierList(), comments);
            checkComments(topclass.getModifierList(), location == JavaOptions.LOCATION_BEFORE_CLASS, comments);
        }
        else if (location == JavaOptions.LOCATION_BEFORE_CLASS)
        {
            // no package, no imports, no top level class
        }
    }

    private static final Logger logger = Logger.getInstance(UpdateJavaFileCopyright.class.getName());
}