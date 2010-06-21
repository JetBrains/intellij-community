/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.light.LightParameter;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

/**
 * @author ven
 */
public class GrLightParameter extends LightParameter implements GrParameter {
  public static final GrLightParameter[] EMPTY_ARRAY = new GrLightParameter[0];
  private volatile boolean myOptional;

  public GrLightParameter(PsiManager manager, @NotNull String name, PsiIdentifier nameIdentifier, @NotNull PsiType type, PsiElement scope) {
    super(manager, name, nameIdentifier, type, scope);
  }

  public GrTypeElement getTypeElementGroovy() {
    return null;
  }

  public GrExpression getDefaultInitializer() {
    return null;
  }

  public GrLightParameter setOptional(boolean optional) {
    myOptional = optional;
    return this;
  }

  public boolean isOptional() {
    return myOptional;
  }

  public GrExpression getInitializerGroovy() {
    return null;
  }

  public void setType(@Nullable PsiType type) throws IncorrectOperationException {
  }

  @NotNull
  public PsiElement getNameIdentifierGroovy() {
    return myNameIdentifier;
  }

  public PsiType getTypeGroovy() {
    return getType();
  }

  public PsiType getDeclaredType() {
    return getType();
  }

  public void accept(GroovyElementVisitor visitor) {
  }

  public void acceptChildren(GroovyElementVisitor visitor) {

  }
}
