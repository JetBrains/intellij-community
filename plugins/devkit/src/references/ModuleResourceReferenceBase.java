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
import com.intellij.psi.PsiLiteral;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public abstract class ModuleResourceReferenceBase extends RuntimeModuleReferenceBase {
  private final IdeaModuleReference myModuleReference;

  public ModuleResourceReferenceBase(@NotNull PsiElement element, @NotNull PsiLiteral moduleNameElement) {
    super(element);
    myModuleReference = findModuleReference(moduleNameElement);
  }

  public ModuleResourceReferenceBase(PsiElement element, IdeaModuleReference moduleReference) {
    super(element);
    myModuleReference = moduleReference;
  }

  @Nullable
  protected Module resolveModule() {
    if (myModuleReference == null) return null;
    PsiElement resolved = myModuleReference.resolve();
    if (!(resolved instanceof PomTargetPsiElement)) return null;

    PomTarget pomTarget = ((PomTargetPsiElement)resolved).getTarget();
    if (!(pomTarget instanceof IdeaModulePomTarget)) return null;

    return ((IdeaModulePomTarget)pomTarget).getModule();
  }

  @Nullable
  private static IdeaModuleReference findModuleReference(PsiLiteral moduleNameElement) {
    for (PsiReference reference : moduleNameElement.getReferences()) {
      if (reference instanceof IdeaModuleReference) {
        return (IdeaModuleReference)reference;
      }
    }
    return null;
  }
}
