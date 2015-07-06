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
package org.jetbrains.plugins.groovy.lang.resolve.ast.builder;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.impl.light.LightPsiClassBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

public class BuilderHelperLightPsiClass extends LightPsiClassBuilder {

  private final @NotNull GrTypeDefinition myContainingClass;

  public BuilderHelperLightPsiClass(@NotNull GrTypeDefinition containingClass, @NotNull String name) {
    super(containingClass, name);
    myContainingClass = containingClass;
    setScope(myContainingClass);
    setOriginInfo(GrBuilderStrategySupport.ORIGIN_INFO);
    getModifierList().addModifier(PsiModifier.STATIC);
  }

  @NotNull
  @Override
  public PsiClass getContainingClass() {
    return myContainingClass;
  }

  @Override
  public PsiFile getContainingFile() {
    return myContainingClass.getContainingFile();
  }
}
