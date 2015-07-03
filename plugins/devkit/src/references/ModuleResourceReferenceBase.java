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

import com.intellij.openapi.module.Module;
import com.intellij.pom.PomTarget;
import com.intellij.pom.PomTargetPsiElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public abstract class ModuleResourceReferenceBase extends RuntimeModuleReferenceBase {
  @NotNull private final PsiLiteralExpression myModuleNameElement;

  public ModuleResourceReferenceBase(@NotNull PsiElement element, @NotNull PsiLiteralExpression moduleNameElement) {
    super(element);
    myModuleNameElement = moduleNameElement;
  }

  @Nullable
  protected Module resolveModule() {
    IdeaModuleReference moduleNameReference = findModuleReference();
    if (moduleNameReference == null) return null;
    PsiElement resolved = moduleNameReference.resolve();
    if (!(resolved instanceof PomTargetPsiElement)) return null;

    PomTarget pomTarget = ((PomTargetPsiElement)resolved).getTarget();
    if (!(pomTarget instanceof IdeaModulePomTarget)) return null;

    return ((IdeaModulePomTarget)pomTarget).getModule();
  }

  @Nullable
  private IdeaModuleReference findModuleReference() {
    for (PsiReference reference : myModuleNameElement.getReferences()) {
      if (reference instanceof IdeaModuleReference) {
        return (IdeaModuleReference)reference;
      }
    }
    return null;
  }
}
