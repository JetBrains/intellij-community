// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.blocks;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.params.GrParameterListImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.ClosureSyntheticParameter;
import org.jetbrains.plugins.groovy.lang.resolve.MethodTypeInferencer;
import org.jetbrains.plugins.groovy.lang.typing.GroovyPsiClosureType;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import static com.intellij.psi.util.CachedValueProvider.Result.create;
import static org.jetbrains.plugins.groovy.lang.psi.impl.FunctionalExpressionsKt.*;

/**
 * @author ilyas
 */
public class GrClosableBlockImpl extends GrBlockImpl implements GrClosableBlock {

  private final AtomicReference<GrParameter[]> mySyntheticItParameter = new AtomicReference<>();

  public GrClosableBlockImpl(@NotNull IElementType type, CharSequence buffer) {
    super(type, buffer);
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitClosure(this);
  }

  @Override
  public void clearCaches() {
    super.clearCaches();
    mySyntheticItParameter.set(null);
  }

  @Override
  public boolean processDeclarations(@NotNull final PsiScopeProcessor processor,
                                     @NotNull final ResolveState state,
                                     @Nullable final PsiElement lastParent,
                                     @NotNull final PsiElement place) {
    if (lastParent == null) return true;

    if (!super.processDeclarations(processor, state, lastParent, place)) return false;
    if (!processParameters(this, processor, state)) return false;
    if (!processClosureClassMembers(this, processor, state, lastParent, place)) return false;

    return true;
  }

  @Override
  public String toString() {
    return "Closable block";
  }

  @Override
  public GrParameter @NotNull [] getParameters() {
    if (hasParametersSection()) {
      GrParameterListImpl parameterList = getParameterList();
      return parameterList.getParameters();
    }

    return GrParameter.EMPTY_ARRAY;
  }

  @Override
  public GrParameter @NotNull [] getAllParameters() {
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


  @Override
  @NotNull
  public GrParameterListImpl getParameterList() {
    final GrParameterListImpl childByClass = findChildByClass(GrParameterListImpl.class);
    assert childByClass != null;
    return childByClass;
  }

  @Override
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

  @Override
  public boolean hasParametersSection() {
    return getArrow() != null;
  }

  @Override
  public PsiType getType() {
    return TypeInferenceHelper.getCurrentContext().getExpressionType(this, GroovyPsiClosureType::new);
  }

  @Override
  @Nullable
  public PsiType getNominalType() {
    return getType();
  }

  public GrParameter[] getSyntheticItParameter() {
    if (getParent() instanceof GrStringInjection) {
      return GrParameter.EMPTY_ARRAY;
    }
    return mySyntheticItParameter.updateAndGet(
      value -> value == null ? new GrParameter[]{new ClosureSyntheticParameter(this, true)} : value
    );
  }

  @Nullable
  @Override
  public PsiType getOwnerType() {
    return CachedValuesManager.getCachedValue(this, () -> create(doGetOwnerType(this), PsiModificationTracker.MODIFICATION_COUNT));
  }

  @Override
  public GrExpression replaceWithExpression(@NotNull GrExpression newExpr, boolean removeUnnecessaryParentheses) {
    return PsiImplUtil.replaceExpression(this, newExpr, removeUnnecessaryParentheses);
  }

  @Override
  @Nullable
  public PsiType getReturnType() {
    return TypeInferenceHelper.getCurrentContext().getCachedValue(this, GrClosableBlockImpl::doGetReturnType);
  }

  @Nullable
  private static PsiType doGetReturnType(GrClosableBlockImpl t) {
    return GroovyPsiManager.inferType(t, new MethodTypeInferencer(t));
  }

  @Override
  public void removeStatement() throws IncorrectOperationException {
    GroovyPsiElementImpl.removeStatement(this);
  }

  @Override
  public boolean isTopControlFlowOwner() {
    return !(getParent() instanceof GrStringInjection);
  }

  @NotNull
  @Override
  public PsiElement getLBrace() {
    return Objects.requireNonNull(super.getLBrace());
  }
}
