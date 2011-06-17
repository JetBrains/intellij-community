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
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.impl.source.tree.Factory;
import com.intellij.psi.impl.source.tree.LazyParseablePsiElement;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
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
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ven
 */
public abstract class GrBlockImpl extends LazyParseablePsiElement implements GrCodeBlock, GrControlFlowOwner {
  private static final Key<CachedValue<Instruction[]>> CONTROL_FLOW = Key.create("Control flow");

  protected GrBlockImpl(@NotNull IElementType type, CharSequence buffer) {
    super(type, buffer);
  }

  @Override
  public void removeElements(PsiElement[] elements) throws IncorrectOperationException {
    GroovyPsiElementImpl.removeElements(this, elements);
  }

  @Override
  public void acceptChildren(GroovyElementVisitor visitor) {
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

  public Instruction[] getControlFlow() {
    assert isValid();
    CachedValue<Instruction[]> controlFlow = getUserData(CONTROL_FLOW);
    if (controlFlow == null) {
      controlFlow = CachedValuesManager.getManager(getProject()).createCachedValue(new CachedValueProvider<Instruction[]>() {
        @Override
        public Result<Instruction[]> compute() {
          return Result.create(new ControlFlowBuilder(getProject()).buildControlFlow(GrBlockImpl.this), getContainingFile());
        }
      }, false);
      putUserData(CONTROL_FLOW, controlFlow);
    }

    return ControlFlowBuilder.assertValidPsi(controlFlow.getValue());
  }

  public void removeVariable(GrVariable variable) {
    PsiImplUtil.removeVariable(variable);
  }

  public GrVariableDeclaration addVariableDeclarationBefore(GrVariableDeclaration declaration, GrStatement anchor) throws IncorrectOperationException {
    GrStatement statement = addStatementBefore(declaration, anchor);
    assert statement instanceof GrVariableDeclaration;
    return ((GrVariableDeclaration) statement);
  }

  private boolean mayUseNewLinesAsSeparators() {
    PsiElement parent = this;
    while (parent != null) {
      if (parent instanceof GrString) {
        GrString grString = (GrString) parent;
        return !grString.isPlainString();
      }
      parent = parent.getParent();
    }
    return true;
  }

  @NotNull
  public GrStatement[] getStatements() {
    List<GrStatement> result = new ArrayList<GrStatement>();
    for (PsiElement cur = getFirstChild(); cur != null; cur = cur.getNextSibling()) {
      if (cur instanceof GrStatement) result.add((GrStatement)cur);
    }
    return result.toArray(new GrStatement[result.size()]);
  }

  public GrStatement addStatementBefore(@NotNull GrStatement element, GrStatement anchor) throws IncorrectOperationException {
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
      if (!isNls(prev)) {
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
  private static boolean isNls(PsiElement element) {
    if (!TokenSets.WHITE_SPACES_SET.contains(element.getNode().getElementType())) return false;
    String text = element.getText();
    return text.contains("\n") || text.contains("\r");
  }

  @NotNull
  public PsiElement getLBrace() {
    return findPsiChildByType(GroovyTokenTypes.mLCURLY);
  }

  @Nullable
  public PsiElement getRBrace() {
    return findPsiChildByType(GroovyTokenTypes.mRCURLY);
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, PsiElement lastParent, @NotNull PsiElement place) {
    return ResolveUtil.processChildren(this, processor, state, lastParent, place);
  }
}
