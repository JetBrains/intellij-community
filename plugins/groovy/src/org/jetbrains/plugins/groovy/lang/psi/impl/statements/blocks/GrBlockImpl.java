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
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
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

/**
 * @author ven
 */
public abstract class GrBlockImpl extends GroovyPsiElementImpl implements GrCodeBlock, GrControlFlowOwner {
  private volatile CachedValue<Instruction[]> myControlFlow = null;

  public void subtreeChanged() {
    super.subtreeChanged();
    myControlFlow = null;
  }

  public Instruction[] getControlFlow() {
    CachedValue<Instruction[]> controlFlow = myControlFlow;
    if (controlFlow == null) {
      myControlFlow = controlFlow = CachedValuesManager.getManager(getProject()).createCachedValue(new CachedValueProvider<Instruction[]>() {
        @Override
        public Result<Instruction[]> compute() {
          return Result.create(new ControlFlowBuilder(getProject()).buildControlFlow(GrBlockImpl.this, null, null), getContainingFile());
        }
      }, false);
    }

    return controlFlow.getValue();
  }

  public GrBlockImpl(@NotNull ASTNode node) {
    super(node);
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
    return findChildrenByClass(GrStatement.class);
  }

  public GrStatement addStatementBefore(@NotNull GrStatement element, GrStatement anchor) throws IncorrectOperationException {
    if (anchor == null && getRBrace() == null) {
      throw new IncorrectOperationException();
    }

    if (anchor != null && !this.equals(anchor.getParent())) {
      throw new IncorrectOperationException();
    }

    ASTNode elemNode = element.copy().getNode();
    assert elemNode != null;
    PsiElement actualAnchor = anchor == null ? getRBrace() : anchor;
    if (mayUseNewLinesAsSeparators()) {
      PsiElement prev = actualAnchor.getPrevSibling();
      if (prev instanceof GrParameterList && prev.getTextLength() == 0 && prev.getPrevSibling() != null) {
        prev = prev.getPrevSibling();
      }
      if (!isNls(prev)) {
        getNode().addLeaf(GroovyTokenTypes.mNLS, "\n", actualAnchor.getNode());
      }
    }
    final ASTNode anchorNode = actualAnchor.getNode();
    element = (GrStatement)addBefore(element, actualAnchor);
    if (mayUseNewLinesAsSeparators()) {
      getNode().addLeaf(GroovyTokenTypes.mNLS, "\n", anchorNode);
    }
    else {
      getNode().addLeaf(GroovyTokenTypes.mSEMI, ";", anchorNode);
    }
    return element;
  }
  private static boolean isNls(PsiElement element) {
    if (!GroovyTokenTypes.WHITE_SPACES_SET.contains(element.getNode().getElementType())) return false;
    String text = element.getText();
    return text.contains("\n") || text.contains("\r");
  }

  @Nullable
  public PsiElement getLBrace() {
    return findChildByType(GroovyTokenTypes.mLCURLY);
  }

  @Nullable
  public PsiElement getRBrace() {
    return findChildByType(GroovyTokenTypes.mRCURLY);
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, PsiElement lastParent, @NotNull PsiElement place) {
    return ResolveUtil.processChildren(this, processor, state, lastParent, place);
  }
}
