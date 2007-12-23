/*
 *  Copyright 2000-2007 JetBrains s.r.o.
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.enumConstant;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.GrFieldImpl;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstantList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 06.04.2007
 */
public class GrEnumConstantImpl extends GrFieldImpl implements GrEnumConstant {
  public GrEnumConstantImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Enumeration constant";
  }

  public boolean hasModifierProperty(@NonNls @NotNull String property) {
    if (property.equals(PsiModifier.STATIC)) return true;
    if (property.equals(PsiModifier.PUBLIC)) return true;
    if (property.equals(PsiModifier.FINAL)) return true;
    return false;
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitEnumConstant(this);
  }

  @Nullable
  public GrTypeElement getTypeElementGroovy() {
    return null;
  }

  @NotNull
  public PsiType getType() {
    return getManager().getElementFactory().createType(getContainingClass(), PsiSubstitutor.EMPTY);
  }

  @Nullable
  public PsiType getTypeGroovy() {
    return getType();
  }

  @Nullable
  public GrExpression getInitializerGroovy() {
    return null;
  }

  @NotNull
  public PsiClass getContainingClass() {
    PsiElement parent = getParent();
    assert parent instanceof GrEnumConstantList;
    return (PsiClass) parent.getParent().getParent();
  }

  public boolean isProperty() {
    return false;
  }
}
