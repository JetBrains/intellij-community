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
package org.jetbrains.plugins.groovy.editor;

import com.intellij.codeInsight.navigation.actions.TypeDeclarationProvider;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

/**
 * @author Sergey Evdokimov
 */
public class GroovyTypeDeclarationProvider implements TypeDeclarationProvider {
  @Override
  @Nullable
  public PsiElement[] getSymbolTypeDeclarations(@NotNull final PsiElement targetElement) {
    PsiType type;
    if (targetElement instanceof GrVariable){
      type = ((GrVariable)targetElement).getTypeGroovy();
    }
    else if (targetElement instanceof GrMethod){
      type = ((GrMethod)targetElement).getInferredReturnType();
    }
    else {
      return null;
    }
    if (type == null) return null;
    PsiClass psiClass = PsiUtil.resolveClassInType(type);
    return psiClass == null ? null : new PsiElement[] {psiClass};
  }
}
