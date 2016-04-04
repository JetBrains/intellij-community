/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.javaFX.fxml.refs;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.DelegatingGlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.UseScopeEnlarger;
import com.intellij.psi.util.PropertyUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.JavaFxCommonClassNames;
import org.jetbrains.plugins.javaFX.fxml.JavaFxFileTypeFactory;
import org.jetbrains.plugins.javaFX.indexing.JavaFxControllerClassIndex;

/**
 * User: anna
 */
public class JavaFxScopeEnlarger extends UseScopeEnlarger {
  @Nullable
  @Override
  public SearchScope getAdditionalUseScope(@NotNull PsiElement element) {
    PsiClass containingClass = null;
    if (element instanceof PsiField) {
      containingClass = ((PsiField)element).getContainingClass();
    }
    else if (element instanceof PsiParameter) {
      final PsiElement declarationScope = ((PsiParameter)element).getDeclarationScope();
      if (declarationScope instanceof PsiMethod && PropertyUtil.isSimplePropertySetter((PsiMethod)declarationScope)) {
        containingClass = ((PsiMethod)declarationScope).getContainingClass();
      }
    }

    if (containingClass != null) {
      if (element instanceof PsiField && 
          !((PsiField)element).hasModifierProperty(PsiModifier.PUBLIC) && 
          AnnotationUtil.isAnnotated((PsiField)element, JavaFxCommonClassNames.JAVAFX_FXML_ANNOTATION, false) || element instanceof PsiParameter) {
        final Project project = element.getProject();
        final String qualifiedName = containingClass.getQualifiedName();
        if (qualifiedName != null && !JavaFxControllerClassIndex.findFxmlWithController(project, qualifiedName).isEmpty()) {
          final GlobalSearchScope projectScope = GlobalSearchScope.projectScope(project);
          return new DelegatingGlobalSearchScope(projectScope){
            @Override
            public boolean contains(@NotNull VirtualFile file) {
              return super.contains(file) && JavaFxFileTypeFactory.isFxml(file);
            }
          };
        }
      }
    } 

    return null;
  }
}
