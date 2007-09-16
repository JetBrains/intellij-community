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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ControlFlowBuilder;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

/**
 * @author ven
 */
public abstract class GrBlockImpl extends GroovyPsiElementImpl implements GrCodeBlock {
  private Instruction[] myControlFlow = null;

  public void subtreeChanged() {
    super.subtreeChanged();
    myControlFlow = null;
  }

  public Instruction[] getControlFlow() {
    if (myControlFlow == null) {
      myControlFlow = new ControlFlowBuilder().buildControlFlow(this, null, null);
    }

    return myControlFlow;
  }

  public GrBlockImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void removeVariable(GrVariable variable) throws IncorrectOperationException {
    PsiImplUtil.removeVariable(variable);
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

    if (!this.equals(anchor.getParent())) {
      throw new IncorrectOperationException();
    }

    ASTNode elemNode = element.getNode();
    getNode().addChild(elemNode, anchor.getNode());
    if (mayUseNewLinesAsSeparators()) {
      getNode().addLeaf(GroovyTokenTypes.mNLS, "\n", anchor.getNode());
    } else {
      getNode().addLeaf(GroovyTokenTypes.mSEMI, ";", anchor.getNode());
    }
    return (GrStatement) elemNode.getPsi();
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull PsiSubstitutor substitutor, PsiElement lastParent, @NotNull PsiElement place) {
    return ResolveUtil.processChildren(this, processor, substitutor, lastParent, place);
  }
}
