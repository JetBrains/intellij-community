/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.params;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.StubBasedPsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrParametersOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrMultiTypeParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.GrVariableBaseImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrMultiTypeParameterStub;

import java.util.Arrays;

/**
 * @author Max Medvedev
 */
public class GrMultitypeParameterImpl extends GrVariableBaseImpl<GrMultiTypeParameterStub>
  implements GrMultiTypeParameter, StubBasedPsiElement<GrMultiTypeParameterStub> {
  private static final Logger LOG = Logger.getInstance(GrMultitypeParameterImpl.class);

  public GrMultitypeParameterImpl(@NotNull ASTNode node) {
    super(node);
  }

  public GrMultitypeParameterImpl(GrMultiTypeParameterStub stub) {
    super(stub, GroovyElementTypes.MULTI_TYPE_PARAMETER);
  }

  @NotNull
  @Override
  public GrTypeElement[] getTypeElements() {
    return findChildrenByClass(GrTypeElement.class);
  }

  @Override
  public PsiType getDeclaredType() {
    final GrTypeElement[] typeElements = getTypeElements();
    LOG.assertTrue(typeElements.length > 0);
    PsiType[] types = new PsiType[typeElements.length];
    for (int i = 0; i < typeElements.length; i++) {
      types[i] = typeElements[i].getType();
    }
    return TypesUtil.getLeastUpperBoundNullable(Arrays.asList(types), getManager());
    //return PsiIntersectionType.createIntersection(types);
  }

  @Override
  public void setType(@Nullable PsiType type) {
    throw new UnsupportedOperationException("setType for GrMultipleParameterImpl is not implemented");
  }


  @Override
  public GrTypeElement getTypeElementGroovy() {
    throw new UnsupportedOperationException("getTypeElementGroovy for GrMultipleParameterImpl is not implemented");
  }

  @Override
  public GrExpression getDefaultInitializer() {
    return null;
  }

  @Override
  public boolean isOptional() {
    return false;
  }

  @NotNull
  @Override
  public PsiElement getDeclarationScope() {
    final GrParametersOwner owner = PsiTreeUtil.getParentOfType(this, GrParametersOwner.class);
    LOG.assertTrue(owner != null);
    if (owner instanceof GrForClause) return owner.getParent();
    return owner;
  }

  @Override
  public boolean isVarArgs() {
    return false;
  }

  @NotNull
  @Override
  public PsiAnnotation[] getAnnotations() {
    final GrModifierList modifierList = getModifierList();
    return modifierList == null ? PsiAnnotation.EMPTY_ARRAY : modifierList.getAnnotations();
  }

  @Override
  public String toString() {
    return "Multi-type catch parameter";
  }
}

