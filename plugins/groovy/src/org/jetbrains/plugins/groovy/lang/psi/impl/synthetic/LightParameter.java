/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

import com.intellij.psi.impl.light.LightVariableBase;
import com.intellij.psi.*;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class LightParameter extends LightVariableBase implements PsiParameter {
  public static final LightParameter[] EMPTY_ARRAY = new LightParameter[0];
  private String myName;

  public LightParameter(PsiManager manager, String name, PsiIdentifier nameIdentifier, @NotNull PsiType type, PsiElement scope) {
    super(manager, nameIdentifier, type, false, scope);
    myName = name;
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    visitor.visitParameter(this);
  }

  public String toString() {
    return "Light Parameter";
  }

  public boolean isVarArgs() {
    return false;
  }

  @NotNull
  public PsiAnnotation[] getAnnotations() {
    return PsiAnnotation.EMPTY_ARRAY;
  }

  public String getName() {
    return myName;
  }
}
