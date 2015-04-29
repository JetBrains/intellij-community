/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.flow;

import com.intellij.codeInspection.dataFlow.ControlFlowImpl;
import com.intellij.codeInspection.dataFlow.Nullness;
import com.intellij.codeInspection.dataFlow.instructions.GotoInstruction;
import com.intellij.codeInspection.dataFlow.instructions.PopInstruction;
import com.intellij.codeInspection.dataFlow.instructions.PushInstruction;
import com.intellij.codeInspection.dataFlow.instructions.ReturnInstruction;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.flow.instruction.GrAssignInstruction;
import org.jetbrains.plugins.groovy.lang.flow.instruction.GrInstructionVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrFinallyClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrTryCatchStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;

public class GrCFExceptionHelper<V extends GrInstructionVisitor<V>> {

  final GrControlFlowAnalyzerImpl<V> analyzer;
  final Stack<CatchDescriptor> myCatchStack = new Stack<CatchDescriptor>();

  public GrCFExceptionHelper(GrControlFlowAnalyzerImpl<V> analyzer) {
    this.analyzer = analyzer;
  }

  static class CatchDescriptor {
    private final PsiType myType;
    private final PsiParameter myParameter;
    private final @NotNull GrOpenBlock myBlock;
    private final boolean myIsFinally;

    public CatchDescriptor(GrFinallyClause finallyBlock) {
      myType = null;
      myParameter = null;
      assert finallyBlock.getBody() != null;
      myBlock = finallyBlock.getBody();
      myIsFinally = true;
    }

    public CatchDescriptor(PsiParameter parameter, @NotNull GrOpenBlock catchBlock) {
      myType = parameter.getType();
      myParameter = parameter;
      myBlock = catchBlock;
      myIsFinally = false;
    }

    @NotNull
    public GrOpenBlock getBlock() {
      return myBlock;
    }

    @NotNull
    public GrTryCatchStatement getTryStatement() {
      final GrTryCatchStatement tryCatchStatement = PsiTreeUtil.getParentOfType(myBlock, GrTryCatchStatement.class, true);
      assert tryCatchStatement != null;
      return tryCatchStatement;
    }

    public PsiType getType() {
      return myType;
    }

    public boolean isFinally() {
      return myIsFinally;
    }

    public ControlFlowImpl.ControlFlowOffset getJumpOffset(ControlFlowImpl flow) {
      return flow.getStartOffset(isFinally() ? myBlock : myBlock.getParent());
    }

    public PsiParameter getParameter() {
      return myParameter;
    }
  }

  void rethrowException(CatchDescriptor currentDescriptor, boolean catchRethrow) {
    CatchDescriptor nextCatch = findNextCatch(catchRethrow);
    if (nextCatch != null) {
      analyzer.addInstruction(new PushInstruction(getExceptionHolder(nextCatch), null, false));
      analyzer.addInstruction(new PushInstruction(getExceptionHolder(currentDescriptor), null, true));
      analyzer.addInstruction(new GrAssignInstruction<V>(null, null, false));
      analyzer.addInstruction(new PopInstruction());
    }
    addThrowCode(nextCatch, null);
  }

  @Nullable
  CatchDescriptor findNextCatch(boolean catchRethrow) {
    if (myCatchStack.isEmpty()) {
      return null;
    }

    PsiElement currentElement = analyzer.myElementStack.peek();

    CatchDescriptor cd = myCatchStack.get(myCatchStack.size() - 1);
    if (!cd.isFinally() && PsiTreeUtil.isAncestor(cd.getBlock().getParent(), currentElement, false)) {
      int i = myCatchStack.size() - 2;
      while (!catchRethrow &&
             i >= 0 &&
             !myCatchStack.get(i).isFinally() &&
             myCatchStack.get(i).getTryStatement() == cd.getTryStatement()) {
        i--;
      }
      if (i < 0) {
        return null;
      }
      cd = myCatchStack.get(i);
    }

    return cd;
  }

  void addThrowCode(@Nullable CatchDescriptor cd, @Nullable PsiElement explicitThrower) {
    if (cd == null) {
      analyzer.addInstruction(new ReturnInstruction(true, explicitThrower));
      return;
    }

    flushVariablesOnControlTransfer(cd.getBlock());
    analyzer.addInstruction(new GotoInstruction(cd.getJumpOffset(analyzer.myFlow)));
  }

  private void flushVariablesOnControlTransfer(PsiElement stopWhenAncestorOf) {
    for (int i = analyzer.myElementStack.size() - 1; i >= 0; i--) {
      PsiElement scope = analyzer.myElementStack.get(i);
      if (PsiTreeUtil.isAncestor(scope, stopWhenAncestorOf, true)) {
        break;
      }
      if (scope instanceof GrOpenBlock) {
        analyzer.flushCodeBlockVariables((GrOpenBlock)scope);
      }
    }
  }

  void initException(PsiType ref, @Nullable CatchDescriptor cd) {
    if (cd == null) return;
    analyzer.addInstruction(new PushInstruction(getExceptionHolder(cd), null));
    analyzer.addInstruction(new PushInstruction(analyzer.myFactory.createTypeValue(ref, Nullness.NOT_NULL), null));
    analyzer.addInstruction(new GrAssignInstruction<V>(null, null, false));
    analyzer.addInstruction(new PopInstruction());
  }

  DfaVariableValue getExceptionHolder(CatchDescriptor cd) {
    return myExceptionHolders.get(cd.getTryStatement());
  }

  @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
  private FactoryMap<GrTryCatchStatement, DfaVariableValue> myExceptionHolders = new FactoryMap<GrTryCatchStatement, DfaVariableValue>() {
    @Nullable
    @Override
    protected DfaVariableValue create(GrTryCatchStatement key) {
      final String text = "java.lang.Object $exception" + size() + "$";
      final PsiParameter mockVar =
        JavaPsiFacade.getElementFactory(analyzer.myCodeFragment.getManager().getProject()).createParameterFromText(text, null);
      return analyzer.myFactory.getVarFactory().createVariableValue(mockVar, false);
    }
  };
}
