// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.navbar;

import com.intellij.ide.navigationToolbar.AbstractNavBarModelExtension;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

public class GrNavBarModelExtension extends AbstractNavBarModelExtension {

  @Nullable
  @Override
  public String getPresentableText(Object object) {
    return null;
  }

  @Override
  public PsiElement adjustElement(@NotNull PsiElement psiElement) {
    final ProjectFileIndex index = ProjectRootManager.getInstance(psiElement.getProject()).getFileIndex();
    final PsiFile containingFile = psiElement.getContainingFile();
    if (containingFile instanceof GroovyFileBase) {
      final VirtualFile file = containingFile.getVirtualFile();
      if (file != null && (index.isUnderSourceRootOfType(file, JavaModuleSourceRootTypes.SOURCES) || index.isInLibrary(file))) {
        if (psiElement instanceof GroovyFileBase) {
          final GroovyFileBase grFile = (GroovyFileBase)psiElement;
          if (grFile.getViewProvider().getBaseLanguage().equals(GroovyLanguage.INSTANCE)) {
            final PsiClass[] psiClasses = grFile.getClasses();
            if (psiClasses.length == 1 && !grFile.isScript()) {
              return psiClasses[0];
            }
          }
        }
        else if (psiElement instanceof GrTypeDefinition) {
          return psiElement;
        }
      }

      return containingFile;
    }

    return psiElement;
  }
}
