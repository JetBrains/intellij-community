/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.extensions.impl;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

public class TypeCondition extends NamedArgumentDescriptorImpl {

  private final @NotNull PsiType myType;

  public TypeCondition(@NotNull PsiType type) {
    super();
    myType = type;
  }

  public TypeCondition(@NotNull PsiType type, @Nullable PsiElement navigationElement) {
    super(navigationElement);
    myType = type;
  }

  public TypeCondition(@NotNull PsiType type, @Nullable PsiElement navigationElement, @Nullable PsiSubstitutor substitutor) {
    super(navigationElement, substitutor);
    myType = type;
  }

  public TypeCondition(@NotNull PsiType type,
                       @Nullable PsiElement navigationElement,
                       @NotNull PsiSubstitutor substitutor,
                       @NotNull Priority priority) {
    super(priority, navigationElement, substitutor);
    myType = type;
  }

  @Override
  public boolean checkType(@NotNull PsiType type, @NotNull GroovyPsiElement context) {
    return TypesUtil.isAssignable(myType, type, context);
  }
}
