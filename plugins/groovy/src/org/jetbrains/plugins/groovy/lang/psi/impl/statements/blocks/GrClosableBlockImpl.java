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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.blocks;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.params.GrParameterListImpl;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

/**
 * @author ilyas
 */
public class GrClosableBlockImpl extends GrBlockImpl implements GrClosableBlock {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.lang.psi.impl.statements.blocks.GrClosableBlockImpl");
  private GrParameter mySyntheticItParameter;
  private static final String SYNTHETIC_PARAMETER_NAME = "it";

  public GrClosableBlockImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitClosure(this);
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull PsiSubstitutor substitutor, PsiElement lastParent, @NotNull PsiElement place) {
    if (!super.processDeclarations(processor, substitutor, lastParent, place)) return false;

    for (final GrParameter parameter : getParameters()) {
      if (!ResolveUtil.processElement(processor, parameter)) return false;
    }

    return true;
  }

  public String toString() {
    return "Closable block";
  }

  public GrParameter[] getParameters() {
    if (hasParametersSection()) {
      GrParameterListImpl parameterList = getParameterList();
      if (parameterList != null) {
        return parameterList.getParameters();
      }

      return GrParameter.EMPTY_ARRAY;
    }

    return new GrParameter[]{getSyntheticItParameter()};
  }

  public GrParameterListImpl getParameterList() {
    return findChildByClass(GrParameterListImpl.class);
  }

  public boolean hasParametersSection() {
    return findChildByType(GroovyElementTypes.mCLOSABLE_BLOCK_OP) != null;
  }

  public PsiType getType() {
    return getManager().getElementFactory().createTypeByFQClassName("groovy.lang.Closure", getResolveScope());
  }


  public void subtreeChanged() {
    mySyntheticItParameter = null;
  }

  public GrParameter getSyntheticItParameter() {
    if (mySyntheticItParameter == null) {
      try {
        mySyntheticItParameter = GroovyElementFactory.getInstance(getProject()).createParameter(SYNTHETIC_PARAMETER_NAME, null);
      } catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
    return mySyntheticItParameter;
  }

  public GrExpression replaceWithExpression(@NotNull GrExpression newExpr) throws IncorrectOperationException {
    return PsiImplUtil.replaceExpression(this, newExpr);
  }


}