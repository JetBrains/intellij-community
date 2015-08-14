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

import com.intellij.openapi.util.Key;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.patterns.PsiMethodPattern;
import com.intellij.psi.*;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.platform.loader.repository.RuntimeModuleId;

import static com.intellij.patterns.PsiJavaPatterns.psiMethod;

/**
 * @author nik
 */
public abstract class RuntimeModuleReferenceContributorBase extends PsiReferenceContributor {
  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    PsiMethodPattern moduleMethod = psiMethod()
      .withName("module", "moduleLibrary", "moduleTests", "moduleResource")
      .definedInClass(RuntimeModuleId.class.getName());

    registrar.registerReferenceProvider(literalInMethodCallParameter(moduleMethod, 0), new PsiReferenceProvider() {
      @NotNull
      @Override
      public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
        return new PsiReference[]{new IdeaModuleReference(element, false)};
      }
    });

    PsiMethodPattern projectLibraryMethod = psiMethod().withName("projectLibrary").definedInClass(RuntimeModuleId.class.getName());
    registrar.registerReferenceProvider(literalInMethodCallParameter(projectLibraryMethod, 0), new PsiReferenceProvider() {
      @NotNull
      @Override
      public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
        return new PsiReference[]{new ProjectLibraryReference(element, false)};
      }
    });

    final Key<PsiMethod> methodKey = Key.create("RUNTIME_MODULE_METHOD_NAME");
    PsiMethodPattern moduleLibraryMethod = psiMethod().withName("moduleLibrary", "moduleResource").definedInClass(RuntimeModuleId.class.getName()).save(methodKey);
    registrar.registerReferenceProvider(literalInMethodCallParameter(moduleLibraryMethod, 1), new PsiReferenceProvider() {
      @NotNull
      @Override
      public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
        PsiElement[] expressions = getMethodCallArguments(element);
        PsiMethod psiMethod = context.get(methodKey);
        if (expressions != null && psiMethod != null && expressions.length > 0 && expressions[0] instanceof PsiLiteral) {
          PsiLiteral moduleNameElement = (PsiLiteral)expressions[0];
          PsiReference reference;
          if ("moduleLibrary".equals(psiMethod.getName())) {
            reference = new ModuleLibraryReference(element, moduleNameElement);
          }
          else {
            reference = new IdeaRuntimeResourceReference(element, moduleNameElement);
          }
          return new PsiReference[]{reference};
        }
        return PsiReference.EMPTY_ARRAY;
      }
    });
  }

  @Nullable
  protected abstract PsiElement[] getMethodCallArguments(@NotNull PsiElement element);

  protected abstract PsiElementPattern<? extends PsiLiteral, ?> literalInMethodCallParameter(PsiMethodPattern moduleMethod,
                                                                                             int paramIndex);
}
