/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.navbar;

import com.intellij.ide.navigationToolbar.AbstractNavBarModelExtension;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
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
  public PsiElement adjustElement(PsiElement psiElement) {
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
