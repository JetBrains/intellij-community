/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.NullableFunction;
import groovy.lang.Closure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClosureType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.params.GrParameterListImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.ClosureSyntheticParameter;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightVariable;
import org.jetbrains.plugins.groovy.lang.resolve.KnownRecursionManager;
import org.jetbrains.plugins.groovy.lang.resolve.MethodTypeInferencer;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ResolverProcessor;
import org.jetbrains.plugins.groovy.refactoring.GroovyNamesUtil;

import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_LANG_CLOSURE;
import static org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil.*;

/**
 * @author ilyas
 */
public class GrClosableBlockImpl extends GrBlockImpl implements GrClosableBlock {

  private volatile GrParameter[] mySyntheticItParameter;

  public GrClosableBlockImpl(@NotNull IElementType type, CharSequence buffer) {
    super(type, buffer);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitClosure(this);
  }

  @Override
  public void clearCaches() {
    super.clearCaches();
    mySyntheticItParameter = null;
  }

  public boolean processClosureDeclarations(final @NotNull PsiScopeProcessor plainProcessor,
                                            final @NotNull PsiScopeProcessor nonCodeProcessor,
                                            final @NotNull ResolveState state,
                                            final @Nullable PsiElement lastParent,
                                            final @NotNull PsiElement place) {
    if (!processDeclarations(plainProcessor, state, lastParent, place)) return false;
    if (!processOwnerAndDelegate(plainProcessor, nonCodeProcessor, state, place)) return false;

    return true;
  }

  public boolean processDeclarations(final @NotNull PsiScopeProcessor processor,
                                     final @NotNull ResolveState state,
                                     final @Nullable PsiElement lastParent,
                                     final @NotNull PsiElement place) {
    if (lastParent == null) return true;

    if (!super.processDeclarations(processor, state, lastParent, place)) return false;
    if (!processParameters(processor, state, place)) return false;
    if (shouldProcessProperties(processor.getHint(ClassHint.KEY)) && !processElement(processor, getOwner(), state)) return false;
    if (!processClosureClassMembers(processor, state, lastParent, place)) return false;

    return true;
  }

  private boolean processOwnerAndDelegate(@NotNull PsiScopeProcessor processor,
                                          @NotNull PsiScopeProcessor nonCodeProcessor,
                                          @NotNull ResolveState state,
                                          @NotNull PsiElement place) {
    Boolean result = processDelegatesTo(processor, nonCodeProcessor, state, place);
    if (result != null) return result.booleanValue();

    if (!processOwner(processor, nonCodeProcessor, state, place)) return false;
    return true;
  }

  @Nullable
  private Boolean processDelegatesTo(@NotNull PsiScopeProcessor processor,
                                     @NotNull PsiScopeProcessor nonCodeProcessor,
                                     @NotNull ResolveState state,
                                     @NotNull PsiElement place) {
    GrDelegatesToUtil.DelegatesToInfo info = GrDelegatesToUtil.getDelegatesToInfo(place, this);
    if (info == null) {
      return null;
    }

    switch (info.getStrategy()) {
      case Closure.OWNER_FIRST:
        if (!processOwner(processor, nonCodeProcessor, state, place)) return false;
        if (!processDelegate(processor, nonCodeProcessor, state, place, info.getTypeToDelegate())) return false;
        return true;
      case Closure.DELEGATE_FIRST:
        if (!processDelegate(processor, nonCodeProcessor, state, place, info.getTypeToDelegate())) return false;
        if (!processOwner(processor, nonCodeProcessor, state, place)) return false;
        return true;
      case Closure.OWNER_ONLY:
        if (!processOwner(processor, nonCodeProcessor, state, place)) return false;
        return true;
      case Closure.DELEGATE_ONLY:
        if (!processDelegate(processor, nonCodeProcessor, state, place, info.getTypeToDelegate())) return false;
        return true;
      case Closure.TO_SELF:
        return true;
      default:
        return null;
    }
  }

  private boolean processDelegate(@NotNull PsiScopeProcessor processor,
                                  @NotNull PsiScopeProcessor nonCodeProcessor,
                                  @NotNull ResolveState state,
                                  @NotNull PsiElement place,
                                  @Nullable final PsiType classToDelegate) {
    if (classToDelegate == null) return true;

    if (state.get(ResolverProcessor.RESOLVE_CONTEXT) == null) {
      state = state.put(ResolverProcessor.RESOLVE_CONTEXT, this);
    }

    return processAllDeclarationsSeparately(classToDelegate, processor, nonCodeProcessor,
                                            state.put(ResolverProcessor.RESOLVE_CONTEXT, this), place);
  }

  private boolean processClosureClassMembers(@NotNull PsiScopeProcessor processor,
                                             @NotNull ResolveState state,
                                             @Nullable PsiElement lastParent,
                                             @NotNull PsiElement place) {
    final PsiClass closureClass = GroovyPsiManager.getInstance(getProject()).findClassWithCache(GROOVY_LANG_CLOSURE, getResolveScope());
    if (closureClass == null) return true;

    return closureClass.processDeclarations(processor, state.put(ResolverProcessor.RESOLVE_CONTEXT, this), lastParent, place);
  }

  private boolean processParameters(@NotNull PsiScopeProcessor processor,
                                    @NotNull ResolveState state,
                                    @NotNull PsiElement place) {
    if (!shouldProcessProperties(processor.getHint(ClassHint.KEY))) return true;

    if (hasParametersSection()) {
      for (GrParameter parameter : getParameters()) {
        if (!processElement(processor, parameter, state)) return false;
      }
    }
    else if (!isItAlreadyDeclared(place)) {
      GrParameter[] synth = getSyntheticItParameter();
      if (synth.length > 0) {
        if (!processElement(processor, synth[0], state.put(ResolverProcessor.RESOLVE_CONTEXT, this))) return false;
      }
    }
    return true;
  }

  private boolean processOwner(@NotNull PsiScopeProcessor processor,
                               @NotNull PsiScopeProcessor nonCodeProcessor,
                               @NotNull ResolveState state,
                               @NotNull PsiElement place) {
    final PsiElement parent = getParent();
    if (parent == null) return true;

    if (!processStaticImports(processor, getContainingFile(), state, place)) return false;

    return doTreeWalkUp(parent, place, processor, nonCodeProcessor, state);
  }

  private boolean isItAlreadyDeclared(@Nullable PsiElement place) {
    while (place != this && place != null) {
      if (place instanceof GrClosableBlock &&
          !((GrClosableBlock)place).hasParametersSection() &&
          !(place.getParent() instanceof GrStringInjection)) {
        return true;
      }
      place = place.getParent();
    }
    return false;
  }

  public String toString() {
    return "Closable block";
  }

  public GrParameter[] getParameters() {
    if (hasParametersSection()) {
      GrParameterListImpl parameterList = getParameterList();
      return parameterList.getParameters();
    }

    return GrParameter.EMPTY_ARRAY;
  }

  public GrParameter[] getAllParameters() {
    if (getParent() instanceof GrStringInjection) return GrParameter.EMPTY_ARRAY;
    if (hasParametersSection()) return getParameters();
    return getSyntheticItParameter();
  }

  @Override
  @Nullable
  public PsiElement getArrow() {
    return findPsiChildByType(GroovyTokenTypes.mCLOSABLE_BLOCK_OP);
  }

  @Override
  public boolean isVarArgs() {
    return PsiImplUtil.isVarArgs(getParameters());
  }


  @NotNull
  public GrParameterListImpl getParameterList() {
    final GrParameterListImpl childByClass = findChildByClass(GrParameterListImpl.class);
    assert childByClass != null;
    return childByClass;
  }

  public GrParameter addParameter(GrParameter parameter) {
    GrParameterList parameterList = getParameterList();
    if (getArrow() == null) {
      final GrParameterList newParamList = (GrParameterList)addAfter(parameterList, getLBrace());
      parameterList.delete();
      ASTNode next = newParamList.getNode().getTreeNext();
      getNode().addLeaf(GroovyTokenTypes.mCLOSABLE_BLOCK_OP, "->", next);
      return (GrParameter)newParamList.add(parameter);
    }

    return (GrParameter)parameterList.add(parameter);
  }

  public boolean hasParametersSection() {
    return getArrow() != null;
  }

  public PsiType getType() {
    return GrClosureType.create(this, true);
  }

  @Nullable
  public PsiType getNominalType() {
    return getType();
  }

  public GrParameter[] getSyntheticItParameter() {
    if (getParent() instanceof GrStringInjection) {
      return GrParameter.EMPTY_ARRAY;
    }

    GrParameter[] res = mySyntheticItParameter;
    if (res == null) {
      res = new GrParameter[]{new ClosureSyntheticParameter(this)};
      synchronized (this) {
        if (mySyntheticItParameter == null) {
          mySyntheticItParameter = res;
        }
      }
    }

    return res;
  }

  private PsiVariable getOwner() {
    return CachedValuesManager.getCachedValue(this, new CachedValueProvider<PsiVariable>() {
      @Override
      public Result<PsiVariable> compute() {
        final GroovyPsiElement context = PsiTreeUtil.getParentOfType(GrClosableBlockImpl.this, GrTypeDefinition.class, GrClosableBlock.class, GroovyFile.class);
        final PsiElementFactory factory = JavaPsiFacade.getInstance(getProject()).getElementFactory();
        PsiType type = null;
        if (context instanceof GrTypeDefinition) {
          type = factory.createType((PsiClass)context);
        }
        else if (context instanceof GrClosableBlock) {
          type = GrClosureType.create((GrClosableBlock)context, true);
        }
        else if (context instanceof GroovyFile) {
          final PsiClass scriptClass = ((GroovyFile)context).getScriptClass();
          if (scriptClass != null && GroovyNamesUtil.isIdentifier(scriptClass.getName())) type = factory.createType(scriptClass);
        }
        if (type == null) {
          type = TypesUtil.getJavaLangObject(GrClosableBlockImpl.this);
        }

        PsiVariable owner = new GrLightVariable(getManager(), OWNER_NAME, type, GrClosableBlockImpl.this);
        return Result.create(owner, PsiModificationTracker.MODIFICATION_COUNT);
      }
    });
  }

  public GrExpression replaceWithExpression(@NotNull GrExpression newExpr, boolean removeUnnecessaryParentheses) {
    return PsiImplUtil.replaceExpression(this, newExpr, removeUnnecessaryParentheses);
  }

  private static final Function<GrClosableBlock, PsiType> ourTypesCalculator = new NullableFunction<GrClosableBlock, PsiType>() {
    public PsiType fun(GrClosableBlock block) {
      return GroovyPsiManager.inferType(block, new MethodTypeInferencer(block));
    }
  };

  @Nullable
  public PsiType getReturnType() {
    return KnownRecursionManager.getInstance().run(this, new Computable<PsiType>() {
      @Override
      public PsiType compute() {
        return TypeInferenceHelper.getCurrentContext().getExpressionType(GrClosableBlockImpl.this, ourTypesCalculator);
      }
    }, getAllParameters());
  }

  @Override
  public void removeStatement() throws IncorrectOperationException {
    GroovyPsiElementImpl.removeStatement(this);
  }

  @Override
  public boolean isTopControlFlowOwner() {
    return !(getParent() instanceof GrStringInjection);
  }
}
