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
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.GrTopStatement;
import org.jetbrains.plugins.groovy.lang.psi.impl.*;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.params.GrParameterListImpl;
import org.jetbrains.plugins.groovy.lang.resolve.MethodTypeInferencer;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ResolverProcessor;

/**
 * @author ilyas
 */
public class GrClosableBlockImpl extends GrBlockImpl implements GrClosableBlock {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.lang.psi.impl.statements.blocks.GrClosableBlockImpl");
  private GrParameter mySyntheticItParameter;
  private GrVariable myOwner;
  private static final String SYNTHETIC_PARAMETER_NAME = "it";
  private static final String OWNER_NAME = "owner";

  public GrClosableBlockImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitClosure(this);
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull PsiSubstitutor substitutor, PsiElement lastParent, @NotNull PsiElement place) {
    if (processor instanceof ResolverProcessor) ((ResolverProcessor) processor).setCurrentFileResolveContext(this);
    try {
      if (!super.processDeclarations(processor, substitutor, lastParent, place)) return false;

      for (final GrParameter parameter : getParameters()) {
        if (!ResolveUtil.processElement(processor, parameter)) return false;
      }

      if (!ResolveUtil.processElement(processor, getOwner())) return false;

      final PsiClass closureClass = getManager().findClass(GROOVY_LANG_CLOSURE, getResolveScope());
      if (closureClass != null && !closureClass.processDeclarations(processor, substitutor, lastParent, place)) return false;

      return true;
    } finally {
      if (processor instanceof ResolverProcessor) ((ResolverProcessor) processor).setCurrentFileResolveContext(null);
    }
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
    return GrClosureType.create(this);
  }

  @Nullable
  public PsiType getNominalType() {
    return getType();
  }

  public void subtreeChanged() {
    super.subtreeChanged();
    mySyntheticItParameter = null;
  }

  public GrParameter getSyntheticItParameter() {
    if (mySyntheticItParameter == null) {
      try {
        mySyntheticItParameter = GroovyElementFactory.getInstance(getProject()).createParameter(SYNTHETIC_PARAMETER_NAME, null, this);
      } catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
    return mySyntheticItParameter;
  }

  private GrVariable getOwner() {
    if (myOwner == null) {
      final GroovyPsiElement context = PsiTreeUtil.getParentOfType(this, GrTypeDefinition.class, GrClosableBlock.class, GroovyFile.class);
      final PsiElementFactory factory = getManager().getElementFactory();
      PsiType type = null;
      if (context instanceof GrTypeDefinition) {
        type = factory.createType((PsiClass) context);
      } else if (context instanceof GrClosableBlock) {
        type = GrClosureType.create((GrClosableBlock) context);
      } else if (context instanceof GroovyFile) {
        final PsiClass scriptClass = ((GroovyFile) context).getScriptClass();
        if (scriptClass != null) type = factory.createType(scriptClass);
      }
      if (type == null) {
        type = factory.createTypeByFQClassName("java.lang.Object", getResolveScope());
      }

      myOwner = GroovyElementFactory.getInstance(getProject()).createVariableDeclaration(null, OWNER_NAME, null, type).getVariables()[0];
    }

    return myOwner;
  }

  public GrExpression replaceWithExpression(@NotNull GrExpression newExpr, boolean removeUnnecessaryParentheses) throws IncorrectOperationException {
    return PsiImplUtil.replaceExpression(this, newExpr, removeUnnecessaryParentheses);
  }

  private static Function<GrClosableBlock, PsiType> ourTypesCalculator = new Function<GrClosableBlock, PsiType>() {
    public PsiType fun(GrClosableBlock block) {
      return GroovyPsiManager.getInstance(block.getProject()).inferType(block, new MethodTypeInferencer(block));
    }
  };

  public @Nullable PsiType getReturnType(){
    if (GroovyPsiManager.getInstance(getProject()).isTypeBeingInferred(this)) {
      return null;
    }
    return GroovyPsiManager.getInstance(getProject()).getType(this, ourTypesCalculator);
  }
}