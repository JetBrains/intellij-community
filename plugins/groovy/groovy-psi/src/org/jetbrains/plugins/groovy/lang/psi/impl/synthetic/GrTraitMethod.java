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
package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic;

import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightMethod;
import org.jetbrains.annotations.NotNull;

public class GrTraitMethod extends LightMethod implements PsiMirrorElement {

  public GrTraitMethod(@NotNull PsiClass containingClass,
                       @NotNull PsiMethod method,
                       @NotNull PsiSubstitutor substitutor) {
    super(containingClass, method, substitutor);
    setNavigationElement(method);
  }

  @Override
  public boolean hasModifierProperty(@NotNull String name) {
    return name != PsiModifier.ABSTRACT && super.hasModifierProperty(name);
  }

  @NotNull
  @Override
  public PsiMethod getPrototype() {
    return myMethod;
  }
}
