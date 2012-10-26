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
 */
package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic;

import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

/**
 * @author Max Medvedev
 */
public class GrLightLocalVariable extends GrLightVariable implements GrVariable {
  private GrAssignmentExpression myExpr;

  public GrLightLocalVariable(GrAssignmentExpression expr) {
    super(expr.getManager(), expr.getLValue().getText(), CommonClassNames.JAVA_LANG_OBJECT, expr.getLValue());
    myExpr = expr;
  }

  @Override
  public PsiElement getContext() {
    return myExpr;
  }

  @Nullable
  @Override
  public GrExpression getInitializerGroovy() {
    return myExpr.getRValue();
  }

  @Override
  public void setType(@Nullable PsiType type) throws IncorrectOperationException {
    throw new UnsupportedOperationException();
  }

  @Nullable
  @Override
  public GrTypeElement getTypeElementGroovy() {
    return null;
  }

  @Nullable
  @Override
  public PsiType getTypeGroovy() {
    GrExpression initializerGroovy = getInitializerGroovy();
    return initializerGroovy != null ? initializerGroovy.getType() : null;
  }

  @Nullable
  @Override
  public PsiType getDeclaredType() {
    return null;
  }

  @Override
  public void setInitializerGroovy(GrExpression initializer) {
    //todo?
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public PsiElement getNameIdentifierGroovy() {
    GrExpression value = myExpr.getLValue();
    return ObjectUtils.assertNotNull(((GrReferenceExpression)value).getReferenceNameElement());
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    //todo
    throw new UnsupportedOperationException();
  }

  @Override
  public void acceptChildren(GroovyElementVisitor visitor) {
    //todo
    throw new UnsupportedOperationException();
  }
}
