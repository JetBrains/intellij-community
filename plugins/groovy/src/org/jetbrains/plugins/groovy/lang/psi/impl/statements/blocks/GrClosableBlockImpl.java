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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.blocks;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClosureType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.params.GrParameterListImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.ClosureSyntheticParameter;
import org.jetbrains.plugins.groovy.lang.resolve.MethodTypeInferencer;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.PropertyResolverProcessor;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ResolverProcessor;
import org.jetbrains.plugins.groovy.refactoring.GroovyNamesUtil;

/**
 * @author ilyas
 */
public class GrClosableBlockImpl extends GrBlockImpl implements GrClosableBlock {
  private volatile PsiParameter mySyntheticItParameter;
  private GrVariable myOwner;

  public GrClosableBlockImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitClosure(this);
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState _state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    if (lastParent == null || !(place instanceof GroovyPsiElement)) return true;

    ResolveState state = _state.put(ResolverProcessor.RESOLVE_CONTEXT, this);
    if (!super.processDeclarations(processor, state, lastParent, place)) return false;

    for (final PsiParameter parameter : getAllParameters()) {
      if (!ResolveUtil.processElement(processor, parameter, state)) return false;
    }

    if (processor instanceof PropertyResolverProcessor && OWNER_NAME.equals(((PropertyResolverProcessor)processor).getName())) {
      processor.handleEvent(ResolveUtil.DECLARATION_SCOPE_PASSED, this);
    }

    if (!ResolveUtil.processElement(processor, getOwner(), state)) return false;

    final PsiClass closureClass = JavaPsiFacade.getInstance(getProject()).findClass(GROOVY_LANG_CLOSURE, getResolveScope());
    if (closureClass != null) {
      if (!closureClass.processDeclarations(processor, state, lastParent, place)) return false;

      // Process non-code in closures
      PsiType clType = JavaPsiFacade.getInstance(getProject()).getElementFactory().createType(closureClass, PsiSubstitutor.EMPTY);
      if (!ResolveUtil.processNonCodeMethods(clType, processor, (GroovyPsiElement)place)) return false;
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

    }

    return GrParameter.EMPTY_ARRAY;
  }

  public PsiParameter[] getAllParameters() {
    if (hasParametersSection()) return getParameters();
    return new PsiParameter[]{getSyntheticItParameter()};
  }

  @Override
  @Nullable
  public PsiElement getArrow() {
    return findChildByType(GroovyTokenTypes.mCLOSABLE_BLOCK_OP);
  }

  public GrParameterListImpl getParameterList() {
    return findChildByClass(GrParameterListImpl.class);
  }

  public void addParameter(GrParameter parameter) {
    GrParameterList parameterList = getParameterList();
    if (getArrow() == null) {
      ASTNode next = parameterList.getNode().getTreeNext();
      getNode().addLeaf(GroovyTokenTypes.mCLOSABLE_BLOCK_OP, "->", next);
      getNode().addLeaf(GroovyTokenTypes.mNLS, "\n", next);
    }

    parameterList.addParameterToEnd(parameter);
  }

  public boolean hasParametersSection() {
    return getArrow() != null;
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

  public PsiParameter getSyntheticItParameter() {
    if (mySyntheticItParameter == null) {
      PsiParameter fresh = new ClosureSyntheticParameter(this);
      synchronized (this) {
        if (mySyntheticItParameter == null) {
          mySyntheticItParameter = fresh;
        }
      }
    }
    return mySyntheticItParameter;
  }

  private GrVariable getOwner() {
    if (myOwner == null) {
      final GroovyPsiElement context = PsiTreeUtil.getParentOfType(this, GrTypeDefinition.class, GrClosableBlock.class, GroovyFile.class);
      final PsiElementFactory factory = JavaPsiFacade.getInstance(getProject()).getElementFactory();
      PsiType type = null;
      if (context instanceof GrTypeDefinition) {
        type = factory.createType((PsiClass)context);
      }
      else if (context instanceof GrClosableBlock) {
        type = GrClosureType.create((GrClosableBlock)context);
      }
      else if (context instanceof GroovyFile) {
        final PsiClass scriptClass = ((GroovyFile)context).getScriptClass();
        if (scriptClass != null && GroovyNamesUtil.isIdentifier(scriptClass.getName())) type = factory.createType(scriptClass);
      }
      if (type == null) {
        type = TypesUtil.getJavaLangObject(this);
      }

      myOwner = GroovyPsiElementFactory.getInstance(getProject()).createVariableDeclaration(null, null, type, OWNER_NAME).getVariables()[0];
    }

    return myOwner;
  }

  public GrExpression replaceWithExpression(@NotNull GrExpression newExpr, boolean removeUnnecessaryParentheses) {
    return PsiImplUtil.replaceExpression(this, newExpr, removeUnnecessaryParentheses);
  }

  private static final Function<GrClosableBlock, PsiType> ourTypesCalculator = new Function<GrClosableBlock, PsiType>() {
    public PsiType fun(GrClosableBlock block) {
      return GroovyPsiManager.getInstance(block.getProject()).inferType(block, new MethodTypeInferencer(block));
    }
  };

  public
  @Nullable
  PsiType getReturnType() {
    if (GroovyPsiManager.getInstance(getProject()).isTypeBeingInferred(this)) {
      return null;
    }
    return GroovyPsiManager.getInstance(getProject()).getType(this, ourTypesCalculator);
  }

}
