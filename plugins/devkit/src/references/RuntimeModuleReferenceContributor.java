/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.references;

import com.intellij.patterns.PsiMethodPattern;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.platform.loader.repository.RuntimeModuleId;

import static com.intellij.patterns.PsiJavaPatterns.*;

/**
 * @author nik
 */
public class RuntimeModuleReferenceContributor extends PsiReferenceContributor {
  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    PsiMethodPattern moduleMethod = psiMethod().withName("module", "moduleLibrary", "moduleTests", "moduleResource").definedInClass(RuntimeModuleId.class.getName());
    registrar.registerReferenceProvider(literalExpression().methodCallParameter(0, moduleMethod), new PsiReferenceProvider() {
      @NotNull
      @Override
      public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
        return new PsiReference[]{new IdeaModuleReference(element, false)};
      }
    });

    PsiMethodPattern projectLibraryMethod = psiMethod().withName("projectLibrary").definedInClass(RuntimeModuleId.class.getName());
    registrar.registerReferenceProvider(literalExpression().methodCallParameter(0, projectLibraryMethod), new PsiReferenceProvider() {
      @NotNull
      @Override
      public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
        return new PsiReference[]{new ProjectLibraryReference(element, false)};
      }
    });

    PsiMethodPattern moduleLibraryMethod = psiMethod().withName("moduleLibrary", "moduleResource").definedInClass(RuntimeModuleId.class.getName());
    registrar.registerReferenceProvider(literalExpression().methodCallParameter(1, moduleLibraryMethod), new PsiReferenceProvider() {
      @NotNull
      @Override
      public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
        PsiMethodCallExpression parent = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class);
        if (parent != null) {
          PsiExpression[] expressions = parent.getArgumentList().getExpressions();
          if (expressions.length > 0 && expressions[0] instanceof PsiLiteralExpression) {
            PsiLiteralExpression moduleNameElement = (PsiLiteralExpression)expressions[0];
            PsiReference reference;
            if ("moduleLibrary".equals(parent.getMethodExpression().getReferenceName())) {
              reference = new ModuleLibraryReference(element, moduleNameElement);
            }
            else {
              reference = new IdeaRuntimeResourceReference(element, moduleNameElement);
            }
            return new PsiReference[]{reference};
          }
        }
        return PsiReference.EMPTY_ARRAY;
      }
    });
  }
}
