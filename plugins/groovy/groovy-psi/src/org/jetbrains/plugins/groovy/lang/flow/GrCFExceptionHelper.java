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

import com.intellij.codeInspection.dataFlow.ControlFlow;
import com.intellij.codeInspection.dataFlow.Nullness;
import com.intellij.codeInspection.dataFlow.instructions.*;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.flow.instruction.GrAssignInstruction;
import org.jetbrains.plugins.groovy.lang.flow.value.GrDfaValueFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrFinallyClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrTryCatchStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.GrTopStatement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import static com.intellij.psi.CommonClassNames.*;

public class GrCFExceptionHelper {

  private final GrControlFlowAnalyzerImpl myAnalyzer;
  private final DfaValue myString;
  private final DfaValue myRuntimeException;
  private final DfaValue myError;
  final Stack<CatchDescriptor> catchStack = new Stack<CatchDescriptor>();
  final PsiType assertionError;
  final PsiType npe;

  public GrCFExceptionHelper(GrControlFlowAnalyzerImpl analyzer) {
    myAnalyzer = analyzer;
    final GrDfaValueFactory factory = analyzer.factory;
    final PsiElement element = analyzer.codeFragment;
    myString = factory.createTypeValue(TypesUtil.createType(JAVA_LANG_STRING, element), Nullness.NOT_NULL);
    myRuntimeException = factory.createTypeValue(TypesUtil.createType(JAVA_LANG_RUNTIME_EXCEPTION, element), Nullness.NOT_NULL);
    myError = factory.createTypeValue(TypesUtil.createType(JAVA_LANG_ERROR, element), Nullness.NOT_NULL);
    assertionError = TypesUtil.createType(JAVA_LANG_ASSERTION_ERROR, element);
    npe = TypesUtil.createType(JAVA_LANG_NULL_POINTER_EXCEPTION, element);
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

    public ControlFlow.ControlFlowOffset getJumpOffset(ControlFlow flow) {
      return flow.getStartOffset(isFinally() ? myBlock : myBlock.getParent());
    }

    public PsiParameter getParameter() {
      return myParameter;
    }
  }

  void rethrowException(CatchDescriptor currentDescriptor, boolean catchRethrow) {
    CatchDescriptor nextCatch = findNextCatch(catchRethrow);
    if (nextCatch != null) {
      myAnalyzer.addInstruction(new PushInstruction(getExceptionHolder(nextCatch), null, false));
      myAnalyzer.addInstruction(new PushInstruction(getExceptionHolder(currentDescriptor), null, true));
      myAnalyzer.addInstruction(new GrAssignInstruction());
      myAnalyzer.addInstruction(new PopInstruction());
    }
    addThrowCode(nextCatch, null);
  }

  @Nullable
  CatchDescriptor findNextCatch(boolean catchRethrow) {
    if (catchStack.isEmpty()) {
      return null;
    }

    PsiElement currentElement = myAnalyzer.elementStack.peek();

    CatchDescriptor cd = catchStack.get(catchStack.size() - 1);
    if (!cd.isFinally() && PsiTreeUtil.isAncestor(cd.getBlock().getParent(), currentElement, false)) {
      int i = catchStack.size() - 2;
      while (!catchRethrow &&
             i >= 0 &&
             !catchStack.get(i).isFinally() &&
             catchStack.get(i).getTryStatement() == cd.getTryStatement()) {
        i--;
      }
      if (i < 0) {
        return null;
      }
      cd = catchStack.get(i);
    }

    return cd;
  }

  CatchDescriptor findFinally() {
    for (int i = catchStack.size() - 1; i >= 0; i--) {
      CatchDescriptor cd = catchStack.get(i);
      if (cd.isFinally()) return cd;
    }

    return null;
  }

  void addThrowCode(@Nullable CatchDescriptor cd, @Nullable PsiElement explicitThrower) {
    if (cd == null) {
      myAnalyzer.addInstruction(new ReturnInstruction(true, explicitThrower));
      return;
    }

    flushVariablesOnControlTransfer(cd.getBlock());
    myAnalyzer.addInstruction(new GotoInstruction(cd.getJumpOffset(myAnalyzer.flow)));
  }

  void addMethodThrows(@Nullable PsiMethod method, @Nullable PsiElement explicitCall) {
    CatchDescriptor cd = findNextCatch(false);
    if (method != null) {
      PsiClassType[] refs = method.getThrowsList().getReferencedTypes();
      for (PsiClassType ref : refs) {
        myAnalyzer.pushUnknown();
        ConditionalGotoInstruction cond = myAnalyzer.addInstruction(new ConditionalGotoInstruction(null, false, null));
        myAnalyzer.addInstruction(new EmptyStackInstruction());
        initException(ref, cd);
        addThrowCode(cd, explicitCall);
        cond.setOffset(myAnalyzer.flow.getNextOffset());
      }
    }
  }

  private void flushVariablesOnControlTransfer(PsiElement stopWhenAncestorOf) {
    for (int i = myAnalyzer.elementStack.size() - 1; i >= 0; i--) {
      PsiElement scope = myAnalyzer.elementStack.get(i);
      if (PsiTreeUtil.isAncestor(scope, stopWhenAncestorOf, true)) {
        break;
      }
      if (scope instanceof GrOpenBlock) {
        myAnalyzer.flushCodeBlockVariables((GrOpenBlock)scope);
      }
    }
  }

  void initException(PsiType ref, @Nullable CatchDescriptor cd) {
    if (cd == null) return;
    myAnalyzer.addInstruction(new PushInstruction(getExceptionHolder(cd), null));
    myAnalyzer.addInstruction(new PushInstruction(myAnalyzer.factory.createTypeValue(ref, Nullness.NOT_NULL), null));
    myAnalyzer.addInstruction(new GrAssignInstruction());
    myAnalyzer.addInstruction(new PopInstruction());
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
        JavaPsiFacade.getElementFactory(myAnalyzer.codeFragment.getManager().getProject()).createParameterFromText(text, null);
      return myAnalyzer.factory.getVarFactory().createVariableValue(mockVar, false);
    }
  };

  void returnCheckingFinally(boolean viaException, @NotNull PsiElement anchor) {
    final CatchDescriptor finallyDescriptor = findFinally();
    if (finallyDescriptor != null) {
      myAnalyzer.addInstruction(new PushInstruction(myString, null));
      myAnalyzer.addInstruction(new PushInstruction(getExceptionHolder(finallyDescriptor), null));
      myAnalyzer.addInstruction(new GrAssignInstruction());
      myAnalyzer.addInstruction(new PopInstruction());
      myAnalyzer.addInstruction(new GotoInstruction(finallyDescriptor.getJumpOffset(myAnalyzer.flow)));
    }
    else {
      final GrTopStatement containingBlock = PsiTreeUtil.getParentOfType(anchor, GrMethod.class, GrClosableBlock.class);
      if (containingBlock != null) {
        myAnalyzer.addInstruction(new GotoInstruction(myAnalyzer.flow.getEndOffset(containingBlock)));
      }
      else {
        myAnalyzer.addInstruction(new ReturnInstruction(viaException, anchor));
      }
    }
  }

  void addConditionalRuntimeThrow() {
    CatchDescriptor cd = findNextCatch(false);
    if (cd == null) {
      return;
    }

    myAnalyzer.pushUnknown();
    final ConditionalGotoInstruction ifNoException = myAnalyzer.addInstruction(new ConditionalGotoInstruction(null, false, null));
    {
      myAnalyzer.addInstruction(new EmptyStackInstruction());
      myAnalyzer.addInstruction(new PushInstruction(getExceptionHolder(cd), null));

      myAnalyzer.pushUnknown();
      final ConditionalGotoInstruction ifError = myAnalyzer.addInstruction(new ConditionalGotoInstruction(null, false, null));
      myAnalyzer.push(myRuntimeException);
      final GotoInstruction ifRuntime = myAnalyzer.addInstruction(new GotoInstruction(null));
      ifError.setOffset(myAnalyzer.flow.getNextOffset());
      myAnalyzer.push(myError);
      ifRuntime.setOffset(myAnalyzer.flow.getNextOffset());

      myAnalyzer.addInstruction(new GrAssignInstruction());
      myAnalyzer.pop();

      addThrowCode(cd, null);
    }
    ifNoException.setOffset(myAnalyzer.flow.getInstructionCount());
  }
}
