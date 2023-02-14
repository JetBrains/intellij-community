// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.blocks;

import com.intellij.extapi.psi.ASTDelegatePsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.source.tree.Factory;
import com.intellij.psi.impl.source.tree.LazyParseablePsiElement;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.profiling.ResolveProfiler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ControlFlowBuilder;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.GroovyControlFlow;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import static org.jetbrains.plugins.groovy.lang.resolve.ResolveUtilKt.processLocals;

public abstract class GrBlockImpl extends LazyParseablePsiElement implements GrCodeBlock, GrControlFlowOwner {
  private static final Key<CachedValue<GroovyControlFlow>> CONTROL_FLOW = Key.create("Control flow");

  protected GrBlockImpl(@NotNull IElementType type, CharSequence buffer) {
    super(type, buffer);
  }

  @Override
  public void delete() throws IncorrectOperationException {
    if (getParent() instanceof ASTDelegatePsiElement) {
      CheckUtil.checkWritable(this);
      ((ASTDelegatePsiElement)getParent()).deleteChildInternal(getNode());
    }
    else {
      getParent().deleteChildRange(this, this);
    }
  }

  @Override
  public void removeElements(PsiElement[] elements) throws IncorrectOperationException {
    GroovyPsiElementImpl.removeElements(this, elements);
  }

  @Override
  public void acceptChildren(@NotNull GroovyElementVisitor visitor) {
    GroovyPsiElementImpl.acceptGroovyChildren(this, visitor);
  }

  public <T extends GrStatement> T replaceWithStatement(T statement) {
    return GroovyPsiElementImpl.replaceWithStatement(this, statement);
  }

  @Override
  public void clearCaches() {
    super.clearCaches();
    putUserData(CONTROL_FLOW, null);
  }

  @Override
  public void deleteChildInternal(@NotNull ASTNode child) {
    final PsiElement element = child.getPsi();
    if (element instanceof GrStatement) {
      PsiImplUtil.deleteStatementTail(this, element);
    }
    super.deleteChildInternal(child);
  }

  @Override
  public void deleteChildRange(PsiElement first, PsiElement last) throws IncorrectOperationException {
    if (last instanceof GrStatement) {
      PsiImplUtil.deleteStatementTail(this, last);
    }
    super.deleteChildRange(first, last);
  }

  @Override
  public Instruction[] getControlFlow() {
    return getGroovyControlFlow().getFlow();
  }

  @NotNull
  public GroovyControlFlow getGroovyControlFlow() {
    PsiUtilCore.ensureValid(this);
    CachedValue<GroovyControlFlow> controlFlow = getUserData(CONTROL_FLOW);
    if (controlFlow == null) {
      controlFlow = CachedValuesManager.getManager(getProject()).createCachedValue(() -> {
        try {
          ResolveProfiler.start();
          final GroovyControlFlow flow = ControlFlowBuilder.buildControlFlow(this);
          return CachedValueProvider.Result.create(flow, getContainingFile(), PsiModificationTracker.MODIFICATION_COUNT);
        }
        finally {
          final long time = ResolveProfiler.finish();
          ResolveProfiler.write("flow", this, time);
        }
      }, false);
      controlFlow = putUserDataIfAbsent(CONTROL_FLOW, controlFlow);
    }
    return controlFlow.getValue();
  }

  @Override
  public void removeVariable(GrVariable variable) {
    PsiImplUtil.removeVariable(variable);
  }

  @Override
  public GrVariableDeclaration addVariableDeclarationBefore(GrVariableDeclaration declaration, GrStatement anchor) throws IncorrectOperationException {
    GrStatement statement = addStatementBefore(declaration, anchor);
    assert statement instanceof GrVariableDeclaration;
    return ((GrVariableDeclaration) statement);
  }

  private boolean mayUseNewLinesAsSeparators() {
    PsiElement parent = this;
    while (parent != null) {
      if (parent instanceof GrString grString) {
        return !grString.isPlainString();
      }
      parent = parent.getParent();
    }
    return true;
  }

  @Override
  public GrStatement @NotNull [] getStatements() {
    return  PsiImplUtil.getStatements(this);
  }

  @Override
  @NotNull
  public GrStatement addStatementBefore(@NotNull GrStatement element, @Nullable GrStatement anchor) throws IncorrectOperationException {
    if (anchor == null && getRBrace() == null) {
      throw new IncorrectOperationException();
    }

    if (anchor != null && !this.equals(anchor.getParent())) {
      throw new IncorrectOperationException();
    }

    final LeafElement nls = Factory.createSingleLeafElement(GroovyTokenTypes.mNLS, "\n", 0, 1, null, getManager());

    PsiElement actualAnchor = anchor == null ? getRBrace() : anchor;
    if (mayUseNewLinesAsSeparators()) {
      PsiElement prev = actualAnchor.getPrevSibling();
      if (prev instanceof GrParameterList && prev.getTextLength() == 0 && prev.getPrevSibling() != null) {
        prev = prev.getPrevSibling();
      }
      if (!PsiUtil.isLineFeed(prev)) {
        addBefore(nls.getPsi(), actualAnchor);
      }
    }
    element = (GrStatement)addBefore(element, actualAnchor);
    if (mayUseNewLinesAsSeparators()) {
      addBefore(nls.getPsi(), actualAnchor);
    }
    else {
      addBefore(Factory.createSingleLeafElement(GroovyTokenTypes.mNLS, "\n", 0, 1, null, getManager()).getPsi(), actualAnchor);
    }
    return element;
  }

  @Override
  public PsiElement getLBrace() {
    return findPsiChildByType(GroovyTokenTypes.mLCURLY);
  }

  @Override
  @Nullable
  public PsiElement getRBrace() {
    return findPsiChildByType(GroovyTokenTypes.mRCURLY);
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     @Nullable PsiElement lastParent,
                                     @NotNull PsiElement place) {
    return processLocals(this, processor, state, lastParent, place);
  }
}
