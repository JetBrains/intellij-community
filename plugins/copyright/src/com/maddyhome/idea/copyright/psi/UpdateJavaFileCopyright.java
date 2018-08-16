// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.maddyhome.idea.copyright.psi;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.maddyhome.idea.copyright.CopyrightProfile;
import com.maddyhome.idea.copyright.options.JavaOptions;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class UpdateJavaFileCopyright extends UpdatePsiFileCopyright
{
    public UpdateJavaFileCopyright(Project project, Module module, VirtualFile root, CopyrightProfile options)
    {
        super(project, module, root, options);
    }

    @Override
    protected boolean accept()
    {
        return getFile() instanceof PsiJavaFile;
    }

    @Override
    protected void scanFile()
    {
        logger.debug("updating " + getFile().getVirtualFile());

        PsiClassOwner javaFile = (PsiClassOwner)getFile();
        PsiElement pkg = getPackageStatement();
        PsiElement[] imports = getImportsList();
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

        if (imports != null && imports.length > 0)
        {
            checkComments(first, imports[0], location == JavaOptions.LOCATION_BEFORE_IMPORT);
            first = imports[0];
        }
        else if (location == JavaOptions.LOCATION_BEFORE_IMPORT)
        {
            location = JavaOptions.LOCATION_BEFORE_CLASS;
        }

        if (topclass != null)
        {
            final List<PsiComment> comments = new ArrayList<>();
            collectComments(first, topclass, comments);
            collectComments(topclass.getFirstChild(), topclass.getModifierList(), comments);
          checkCommentsForTopClass(topclass, location, comments);
        }
        else if (location == JavaOptions.LOCATION_BEFORE_CLASS)
        {
            // no package, no imports, no top level class
        }
    }

  protected void checkCommentsForTopClass(PsiClass topclass, int location, List<PsiComment> comments) {
    checkComments(topclass.getModifierList(), location == JavaOptions.LOCATION_BEFORE_CLASS, comments);
  }

  @Nullable
  protected PsiElement[] getImportsList() {
    final PsiJavaFile javaFile = (PsiJavaFile)getFile();
    assert javaFile != null;
    final PsiImportList importList = javaFile.getImportList();
    return importList == null ? null : importList.getChildren();
  }

  @Nullable
  protected PsiElement getPackageStatement() {
    PsiJavaFile javaFile = (PsiJavaFile)getFile();
    assert javaFile != null;
    return javaFile.getPackageStatement();
  }

  private static final Logger logger = Logger.getInstance(UpdateJavaFileCopyright.class.getName());

  public static class UpdateJavaCopyrightsProvider extends UpdateCopyrightsProvider {

    @Override
    public UpdateCopyright createInstance(Project project, Module module, VirtualFile file, FileType base, CopyrightProfile options) {
      return new UpdateJavaFileCopyright(project, module, file, options);
    }
  }
}