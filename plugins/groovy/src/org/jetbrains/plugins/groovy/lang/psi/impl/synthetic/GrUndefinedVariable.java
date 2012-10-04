/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
 *//*

package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic;

import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightVariableBase;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

*/
/**
 * @author Max Medvedev
 *//*

public class GrUndefinedVariable extends LightVariableBase implements GrVariable {
  public GrUndefinedVariable(GrAssignmentExpression assignment) {
    super(assignment.getManager(), new GrImplicitVariableImpl.GrLightIdentifier(assignment.getManager(), assignment.getLValue().getText()),
          PsiType.getJavaLangObject(assignment.getManager(), assignment.getResolveScope()), true, assignment.get);
  }

  @Nullable
  @Override
  public GrExpression getInitializerGroovy() {
    //todo
    return null;
  }

  @Override
  public void setType(@Nullable PsiType type) throws IncorrectOperationException {
    //todo

  }

  @Nullable
  @Override
  public GrTypeElement getTypeElementGroovy() {
    //todo
    return null;
  }

  @Nullable
  @Override
  public PsiType getTypeGroovy() {
    //todo
    return null;
  }

  @Nullable
  @Override
  public PsiType getDeclaredType() {
    //todo
    return null;
  }

  @Nullable
  @Override
  public GrModifierList getModifierList() {
    //todo
    return null;
  }

  @NotNull
  @Override
  public PsiElement getNameIdentifierGroovy() {
    //todo
    return null;
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    //todo

  }

  @Override
  public void acceptChildren(GroovyElementVisitor visitor) {
    //todo

  }

  @Override
  public String toString() {
    //todo
    return null;
  }
}
*/
