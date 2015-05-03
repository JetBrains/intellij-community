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
package org.jetbrains.plugins.groovy.lang.flow;

import com.intellij.codeInspection.dataFlow.ControlFlow;
import com.intellij.codeInspection.dataFlow.ControlFlow.ControlFlowOffset;
import com.intellij.codeInspection.dataFlow.ControlFlowImpl;
import com.intellij.codeInspection.dataFlow.IControlFlowAnalyzer;
import com.intellij.codeInspection.dataFlow.Nullness;
import com.intellij.codeInspection.dataFlow.instructions.*;
import com.intellij.codeInspection.dataFlow.value.DfaRelation;
import com.intellij.codeInspection.dataFlow.value.DfaUnknownValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.flow.GrCFExceptionHelper.CatchDescriptor;
import org.jetbrains.plugins.groovy.lang.flow.instruction.*;
import org.jetbrains.plugins.groovy.lang.flow.value.GrDfaValueFactory;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrCondition;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrAssertStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrBreakStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrContinueStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.arithmetic.GrRangeExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.List;

import static org.jetbrains.plugins.groovy.lang.flow.GrCFExpressionHelper.shouldCheckReturn;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.*;
import static org.jetbrains.plugins.groovy.lang.lexer.TokenSets.ASSIGNMENTS_TO_OPERATORS;

public class GrControlFlowAnalyzerImpl<V extends GrInstructionVisitor<V>>
  extends GroovyRecursiveElementVisitor implements IControlFlowAnalyzer<V> {

  private final GrCFExceptionHelper<V> myExceptionHelper = new GrCFExceptionHelper<V>(this);
  private final GrCFExpressionHelper<V> myExpressionHelper = new GrCFExpressionHelper<V>(this);
  final GrCFCallHelper<V> myCallHelper = new GrCFCallHelper<V>(this);
  final ControlFlowImpl<V> myFlow = new ControlFlowImpl<V>();
  final Stack<PsiElement> myElementStack = new Stack<PsiElement>();
  final GrDfaValueFactory myFactory;
  final PsiElement myCodeFragment;
  private final PsiType myAssertionError;

  public GrControlFlowAnalyzerImpl(@NotNull GrDfaValueFactory factory, @NotNull PsiElement block) {
    myFactory = factory;
    myCodeFragment = block;
    myAssertionError = TypesUtil.createType(CommonClassNames.JAVA_LANG_ASSERTION_ERROR, block);
  }

  @Override
  public ControlFlow<V> buildControlFlow() {
    try {
      myCodeFragment.accept(new GroovyPsiElementVisitor(this) {
        @Override
        public void visitErrorElement(PsiErrorElement element) {
          throw new CannotAnalyzeException();
        }
      });
      //if (myFlow.getInstructionCount() == 0) {
      myFlow.addInstruction(new ReturnInstruction<V>(false, null));
      //}
      return myFlow;
    }
    catch (CannotAnalyzeException ignored) {
      return null;
    }
  }

  @Override
  public void visitFile(GroovyFileBase file) {
    startElement(file);
    for (GrStatement statement : file.getStatements()) {
      statement.accept(this);
    }
    finishElement(file);
  }

  @Override
  public void visitOpenBlock(GrOpenBlock block) {
    startElement(block);
    super.visitOpenBlock(block);
    flushCodeBlockVariables(block);
    finishElement(block);
  }

  @Override
  public void visitBlockStatement(GrBlockStatement blockStatement) {
    startElement(blockStatement);
    super.visitBlockStatement(blockStatement);
    finishElement(blockStatement);
  }

  @Override
  public void visitVariableDeclaration(GrVariableDeclaration variableDeclaration) {
    startElement(variableDeclaration);

    final GrVariable[] variables = variableDeclaration.getVariables();
    if (variableDeclaration.isTuple()) {
      final GrExpression tupleInitializer = variableDeclaration.getTupleInitializer();
      if (tupleInitializer instanceof GrListOrMap) {
        final GrExpression[] initializers = ((GrListOrMap)tupleInitializer).getInitializers();
        // iterate over tuple variables and initialize each 
        for (int i = 0; i < Math.min(variables.length, initializers.length); i++) {
          myExpressionHelper.initialize(variables[i], initializers[i]);
          pop();
        }
        // iterate over rest initializers and evaluate them
        for (int i = variables.length; i < initializers.length; i++) {
          initializers[i].accept(this);
          pop();
        }
      }
    }
    else {
      for (GrVariable variable : variables) {
        final GrExpression initializer = variable.getInitializerGroovy();
        if (initializer != null) {
          myExpressionHelper.initialize(variable, initializer);
          pop();
        }
      }
    }

    finishElement(variableDeclaration);
  }

  @Override
  public void visitAssignmentExpression(final GrAssignmentExpression expression) {
    final GrExpression left = expression.getLValue();
    final GrExpression right = expression.getRValue();

    if (right == null) {
      pushUnknown();
      finishElement(expression);
      return;
    }

    final IElementType op = expression.getOperationTokenType();
    if (op == mASSIGN) {
      startElement(expression);
      if (left instanceof GrTupleExpression) {
        myExpressionHelper.assignTuple(((GrTupleExpression)left).getExpressions(), right);
        pushUnknown(); // so there will be value to pop in finishElement()
      }
      else {
        myExpressionHelper.assign(left, right);
      }
      finishElement(expression);
    }
    else {
      myExpressionHelper.assign(left, right, myCallHelper.new ArgumentsBase() {
        @Override
        public int runArguments() {
          myExpressionHelper.binaryOperation(expression, left, right, ASSIGNMENTS_TO_OPERATORS.get(op), expression.multiResolve(false));
          return 1;
        }
      });
    }
  }

  @Override
  public void visitMethodCallExpression(GrMethodCallExpression methodCallExpression) {
    startElement(methodCallExpression);
    myCallHelper.processMethodCall(methodCallExpression);
    finishElement(methodCallExpression);
  }

  @Override
  public void visitApplicationStatement(GrApplicationStatement applicationStatement) {
    startElement(applicationStatement);
    myCallHelper.processMethodCall(applicationStatement);
    finishElement(applicationStatement);
  }

  @Override
  public void visitNewExpression(GrNewExpression expression) {
    startElement(expression);

    pushUnknown(); // qualifier

    final GrArrayDeclaration arrayDeclaration = expression.getArrayDeclaration();
    if (arrayDeclaration != null) {
      for (GrExpression dimension : arrayDeclaration.getBoundExpressions()) {
        dimension.accept(this);
        myExpressionHelper.boxUnbox(PsiType.INT, dimension.getType());
        pop();
      }
    }
    else {
      myCallHelper.visitArguments(expression);
      addInstruction(new GrMethodCallInstruction(expression, null));
      //if (!myCatchStack.isEmpty()) {
      //  addMethodThrows(ctr, expression);
      //}
    }

    finishElement(expression);
  }

  @Override
  public void visitParenthesizedExpression(GrParenthesizedExpression expression) {
    startElement(expression);

    final GrExpression operand = expression.getOperand();
    if (operand == null) {
      throw new CannotAnalyzeException();
    }
    operand.accept(this);

    finishElement(expression);
  }

  @Override
  public void visitIfStatement(GrIfStatement statement) {
    startElement(statement);

    final GrExpression condition = statement.getCondition();
    final GrStatement thenBranch = statement.getThenBranch();
    final GrStatement elseBranch = statement.getElseBranch();
    final ControlFlowOffset ifFalseOffset = elseBranch != null
                                            ? myFlow.getStartOffset(elseBranch)
                                            : myFlow.getEndOffset(statement);

    if (condition != null) {
      condition.accept(this);
      coerceToBoolean();
      addInstruction(new ConditionalGotoInstruction(ifFalseOffset, true, condition));
    }

    if (thenBranch != null) {
      thenBranch.accept(this);
    }

    if (elseBranch != null) {
      addInstruction(new GotoInstruction(myFlow.getEndOffset(statement)));
      elseBranch.accept(this);
    }

    finishElement(statement);
  }

  @Override
  public void visitSwitchStatement(GrSwitchStatement switchStatement) {
    startElement(switchStatement);

    final GrExpression condition = switchStatement.getCondition();
    if (condition == null) {
      finishElement(switchStatement);
      return;
    }
    condition.accept(this);

    GotoInstruction fallbackGoto = null;
    for (GrCaseSection section : switchStatement.getCaseSections()) {
      startElement(section);

      final List<ConditionalGotoInstruction> gotosToBlock = processCaseSection(condition, section);
      final ControlFlowOffset statementsBlockOffset = myFlow.getNextOffset();
      for (ConditionalGotoInstruction aGoto : gotosToBlock) {
        aGoto.setOffset(statementsBlockOffset);
      }
      if (fallbackGoto != null) {
        fallbackGoto.setOffset(statementsBlockOffset);
      }
      for (GrStatement statement : section.getStatements()) {
        statement.accept(this);
      }
      fallbackGoto = addInstruction(new GotoInstruction<V>(null));

      finishElement(section);
    }

    if (fallbackGoto != null) {
      // last goto falls back to the very end
      fallbackGoto.setOffset(myFlow.getEndOffset(switchStatement));
    }

    finishElement(switchStatement);

    // now pop switch condition
    pop();
  }

  /**
   * @param condition
   * @param section
   * @return empty gotos to statements block
   */
  private List<ConditionalGotoInstruction> processCaseSection(@NotNull GrExpression condition, @NotNull GrCaseSection section) {
    final List<ConditionalGotoInstruction> result = ContainerUtil.newArrayList();
    final GrCaseLabel[] labels = section.getCaseLabels();
    for (int i = 0, length = labels.length; i < length; i++) {
      final GrCaseLabel caseLabel = labels[i];
      startElement(caseLabel);

      // put case expression on top of the stack
      final GrExpression labelValue = caseLabel.getValue();
      if (labelValue == null) {
        pushUnknown();
      }
      else {
        // duplicate evaluated condition on top of the stack
        addInstruction(new DupInstruction<V>());
        labelValue.accept(this);
        if (processCaseCall(condition, labelValue)) {
          pop(); // pop label value
          pop(); // pop duplicated condition
          pushUnknown();
        }
      }

      if (i == labels.length - 1) {
        // if not matched then go to next case section
        // if matched then next instruction is the start of the statements block
        addInstruction(new ConditionalGotoInstruction<V>(
          myFlow.getEndOffset(section),
          true,
          labelValue
        ));
      }
      else {
        // if matched go to the statements block
        // if not matched then next instruction is the start of the next case label
        result.add(addInstruction(new ConditionalGotoInstruction<V>(
          null,
          false,
          labelValue
        )));
      }

      finishElement(caseLabel);
    }
    return result;
  }

  private boolean processCaseCall(@NotNull GrExpression condition, @NotNull GrExpression caseValue) {
    if (caseValue instanceof GrLiteral) {
      addInstruction(new BinopInstruction<V>(DfaRelation.EQ, caseValue));
      return false;
    }
    if (caseValue instanceof GrReferenceExpression && ((GrReferenceExpression)caseValue).resolve() instanceof PsiClass) {
      addInstruction(new BinopInstruction<V>(DfaRelation.INSTANCEOF, caseValue));
      return false;
    }
    final PsiType caseType = caseValue.getType();
    if (caseType != null) {
      final GroovyResolveResult[] cases = ResolveUtil.getMethodCandidates(caseType, "isCase", caseValue, condition.getType());
      if (cases.length == 1 && cases[0] != GroovyResolveResult.EMPTY_RESULT) {
        addInstruction(new GrMethodCallInstruction<V>(caseValue, new GrExpression[]{condition}, cases[0]));
        return false;
      }
    }
    return true;
  }

  @Override
  public void visitBreakStatement(GrBreakStatement statement) {
    final GrStatement targetStatement = statement.findTargetStatement();
    if (targetStatement != null) {
      addInstruction(new GotoInstruction<V>(myFlow.getEndOffset(targetStatement)));
    }
  }

  @Override
  public void visitContinueStatement(GrContinueStatement statement) {
    final GrStatement targetStatement = statement.findTargetStatement();
    if (targetStatement != null) {
      addInstruction(new GotoInstruction<V>(myFlow.getStartOffset(targetStatement)));
    }
  }

  @Override
  public void visitForStatement(GrForStatement statement) {
    startElement(statement);
    final GrForClause clause = statement.getClause();
    final GrVariable parameter = clause == null ? null : clause.getDeclaredVariable();
    if (clause instanceof GrTraditionalForClause) {
      final GrTraditionalForClause traditionalForClause = (GrTraditionalForClause)clause;

      final GrCondition initialization = traditionalForClause.getInitialization();
      if (initialization != null) {
        initialization.accept(this);
        pop();
      }

      final GrExpression condition = traditionalForClause.getCondition();
      if (condition != null) {
        condition.accept(this);
      }
      else {
        pushUnknown();
      }
      addInstruction(new ConditionalGotoInstruction<V>(myFlow.getEndOffset(statement), true, condition));

      final GrStatement body = statement.getBody();
      if (body != null) {
        body.accept(this);
      }
      final GrExpression update = traditionalForClause.getUpdate();
      if (update != null) {
        update.accept(this);
        pop();
      }
      addInstruction(new GotoInstruction<V>(myFlow.getStartOffset(condition)));
    }
    else if (clause instanceof GrForInClause) {
      final GrForInClause forInClause = (GrForInClause)clause;

      final GrExpression iteratedValue = forInClause.getIteratedExpression();
      if (iteratedValue != null) {
        iteratedValue.accept(this);
        addInstruction(new GrDereferenceInstruction<V>(iteratedValue));
      }

      final ControlFlowImpl.ControlFlowOffset loopStartOffset = myFlow.getNextOffset();
      removeVariable(parameter);

      pushUnknown();
      addInstruction(new ConditionalGotoInstruction(myFlow.getEndOffset(statement), true, null));

      final GrStatement body = statement.getBody();
      if (body != null) {
        body.accept(this);
      }

      addInstruction(new GotoInstruction(loopStartOffset));
    }

    finishElement(statement);
    removeVariable(parameter);
  }

  @Override
  public void visitWhileStatement(GrWhileStatement whileStatement) {
    startElement(whileStatement);

    final GrExpression condition = whileStatement.getCondition();
    if (condition == null) {
      pushUnknown();
    }
    else {
      condition.accept(this);
    }
    addInstruction(new ConditionalGotoInstruction<V>(myFlow.getEndOffset(whileStatement), true, condition));

    final GrStatement body = whileStatement.getBody();
    if (body != null) {
      body.accept(this);
    }
    addInstruction(new GotoInstruction<V>(myFlow.getStartOffset(whileStatement)));

    finishElement(whileStatement);
  }

  @Override
  public void visitTryStatement(GrTryCatchStatement statement) {
    startElement(statement);

    GrOpenBlock tryBlock = statement.getTryBlock();
    GrFinallyClause finallyBlock = statement.getFinallyClause();

    if (finallyBlock != null) {
      myExceptionHelper.myCatchStack.push(new CatchDescriptor(finallyBlock));
    }

    GrCatchClause[] sections = statement.getCatchClauses();
    for (int i = sections.length - 1; i >= 0; i--) {
      GrCatchClause section = sections[i];
      GrOpenBlock catchBlock = section.getBody();
      PsiParameter parameter = section.getParameter();
      if (parameter != null && catchBlock != null) {
        PsiType type = parameter.getType();
        if (type instanceof PsiClassType || type instanceof PsiDisjunctionType) {
          myExceptionHelper.myCatchStack.push(new CatchDescriptor(parameter, catchBlock));
          continue;
        }
      }
      throw new CannotAnalyzeException();
    }

    final ControlFlowImpl.ControlFlowOffset endOffset = finallyBlock == null
                                                        ? myFlow.getEndOffset(statement)
                                                        : myFlow.getStartOffset(finallyBlock);

    tryBlock.accept(this);
    addInstruction(new GotoInstruction(endOffset));

    for (GrCatchClause section : sections) {
      section.accept(this);
      addInstruction(new GotoInstruction(endOffset));
      myExceptionHelper.myCatchStack.pop();
    }

    if (finallyBlock != null) {
      CatchDescriptor finallyDescriptor = myExceptionHelper.myCatchStack.pop();
      finallyBlock.accept(this);

      //if $exception$==null => continue normal execution
      addInstruction(new PushInstruction(myExceptionHelper.getExceptionHolder(finallyDescriptor), null));
      addInstruction(new PushInstruction(myFactory.getConstFactory().getNull(), null));
      addInstruction(new BinopInstruction(DfaRelation.EQ, null, statement.getProject()));
      addInstruction(new ConditionalGotoInstruction(myFlow.getEndOffset(statement), false, null));

      // else throw $exception$
      myExceptionHelper.rethrowException(finallyDescriptor, false);
    }

    finishElement(statement);
  }

  @Override
  public void visitCatchClause(GrCatchClause catchClause) {
    startElement(catchClause);

    final GrOpenBlock catchBlock = catchClause.getBody();
    final GrParameter catchClauseParameter = catchClause.getParameter();
    if (catchBlock == null || catchClauseParameter == null) {
      finishElement(catchClause);
      return;
    }

    final CatchDescriptor currentDescriptor = new CatchDescriptor(catchClauseParameter, catchBlock);
    final DfaVariableValue exceptionHolder = myExceptionHelper.getExceptionHolder(currentDescriptor);

    // exception is in exceptionHolder mock variable
    // check if it's assignable to catch parameter type
    PsiType declaredType = catchClauseParameter.getType();
    List<PsiType> flattened = declaredType instanceof PsiDisjunctionType ?
                              ((PsiDisjunctionType)declaredType).getDisjunctions() :
                              ContainerUtil.createMaybeSingletonList(declaredType);
    for (PsiType catchType : flattened) {
      addInstruction(new PushInstruction(exceptionHolder, null));
      addInstruction(new PushInstruction(myFactory.createTypeValue(catchType, Nullness.UNKNOWN), null));
      addInstruction(new BinopInstruction(DfaRelation.INSTANCEOF, null, catchClause.getProject()));
      addInstruction(new ConditionalGotoInstruction(ControlFlowImpl.deltaOffset(myFlow.getStartOffset(catchBlock), -5), false, null));
    }

    // not assignable => rethrow 
    myExceptionHelper.rethrowException(currentDescriptor, true);

    // e = $exception$
    addInstruction(new PushInstruction(myFactory.getVarFactory().createVariableValue(catchClauseParameter, false), null));
    addInstruction(new PushInstruction(exceptionHolder, null));
    addInstruction(new GrAssignInstruction<V>(null, null, false));
    addInstruction(new PopInstruction());

    addInstruction(new FlushVariableInstruction(exceptionHolder));

    catchBlock.accept(this);

    finishElement(catchClause);
  }

  @Override
  public void visitAssertStatement(GrAssertStatement assertStatement) {
    startElement(assertStatement);
    final GrExpression condition = assertStatement.getAssertion();
    final GrExpression description = assertStatement.getErrorMessage();
    if (condition != null) {
      condition.accept(this);

      addInstruction(new ConditionalGotoInstruction(myFlow.getEndOffset(assertStatement), false, condition));
      if (description != null) {
        description.accept(this);
      }

      CatchDescriptor cd = myExceptionHelper.findNextCatch(false);
      myExceptionHelper.initException(myAssertionError, cd);
      myExceptionHelper.addThrowCode(cd, assertStatement);
    }
    finishElement(assertStatement);
  }

  @Override
  public void visitParameter(GrParameter parameter) {
    startElement(parameter);
    final GrExpression initializer = parameter.getInitializerGroovy();
    if (initializer != null) {
      myExpressionHelper.initialize(parameter, initializer);
    }
    finishElement(parameter);
  }

  @Override
  public void visitElvisExpression(GrElvisExpression expression) {
    startElement(expression);

    final GrExpression condition = expression.getCondition();
    condition.accept(this);
    coerceToBoolean();
    addInstruction(new DupInstruction<V>());
    addInstruction(new ConditionalGotoInstruction<V>(myFlow.getEndOffset(expression), false, condition));
    pop();
    final GrExpression elseBranch = expression.getElseBranch();
    if (elseBranch == null) {
      pushUnknown();
    }
    else {
      elseBranch.accept(this);
    }

    finishElement(expression);
  }

  @Override
  public void visitConditionalExpression(GrConditionalExpression expression) {
    startElement(expression);

    final GrExpression condition = expression.getCondition();
    final GrExpression thenBranch = expression.getThenBranch();
    final GrExpression elseBranch = expression.getElseBranch();
    condition.accept(this);
    coerceToBoolean();
    final ConditionalGotoInstruction<V> gotoElse = addInstruction(new ConditionalGotoInstruction<V>(null, true, condition));

    if (thenBranch == null) {
      pushUnknown();
    }
    else {
      thenBranch.accept(this);
    }
    addInstruction(new GotoInstruction<V>(myFlow.getEndOffset(expression)));

    gotoElse.setOffset(myFlow.getNextOffset());
    if (elseBranch == null) {
      pushUnknown();
    }
    else {
      elseBranch.accept(this);
    }

    finishElement(expression);
  }

  @Override
  public void visitReferenceExpression(GrReferenceExpression referenceExpression) {
    startElement(referenceExpression);

    final boolean writing = PsiUtil.isAccessedForWriting(referenceExpression);
    final GrExpression qualifierExpression = referenceExpression.getQualifierExpression();
    if (qualifierExpression == null) {
      push(myFactory.createValue(referenceExpression), referenceExpression, writing);
    }
    else {
      qualifierExpression.accept(this);
      final IElementType dot = referenceExpression.getDotTokenType();
      if (dot == mOPTIONAL_DOT || dot == mSPREAD_DOT) {
        addInstruction(new DupInstruction<V>()); // save qualifier for later use
        pushNull();
        addInstruction(new BinopInstruction(DfaRelation.EQ, referenceExpression));
        final ConditionalGotoInstruction gotoToNotNull = addInstruction(new ConditionalGotoInstruction(null, true, qualifierExpression));

        // null branch
        pop();        // pop duplicated qualifier
        pushNull();
        final GotoInstruction<V> gotoEnd = addInstruction(new GotoInstruction<V>(null));
        gotoToNotNull.setOffset(myFlow.getNextOffset());

        // not null branch
        // qualifier is on top of stack
        myExpressionHelper.dereference(qualifierExpression, referenceExpression, writing);
        gotoEnd.setOffset(myFlow.getNextOffset());
      }
      else {
        myExpressionHelper.dereference(qualifierExpression, referenceExpression, writing);
      }
    }

    finishElement(referenceExpression);
  }

  @Override
  public void visitUnaryExpression(GrUnaryExpression expression) {
    startElement(expression);

    final GrExpression operand = expression.getOperand();
    if (operand == null) {
      pushUnknown();
    }
    else {
      operand.accept(this);
      if (expression.getOperationTokenType() == mLNOT) {
        coerceToBoolean();
        addInstruction(new NotInstruction<V>());
      }
      else {
        final GroovyResolveResult[] results = expression.multiResolve(false);
        if (results.length == 1) {
          myCallHelper.processMethodCallStraight(expression, results[0]);
        }
        else {
          pop();
          pushUnknown();
        }
      }
    }

    finishElement(expression);
  }

  @Override
  public void visitBinaryExpression(GrBinaryExpression expression) {
    startElement(expression);

    GrExpression left = expression.getLeftOperand();
    GrExpression right = expression.getRightOperand();

    if (right == null) {
      pushUnknown();
      finishElement(expression);
      return;
    }

    final IElementType operatorToken = expression.getOperationTokenType();
    if (operatorToken == mLAND || operatorToken == mLOR) {
      final boolean isAnd = operatorToken == mLAND;
      left.accept(this);
      coerceToBoolean();
      addInstruction(new DupInstruction<V>());
      addInstruction(new ConditionalGotoInstruction<V>(myFlow.getEndOffset(expression), isAnd, left));
      pop();
      right.accept(this);

      coerceToBoolean();
      final ConditionalGotoInstruction<V> gotoSuccess = addInstruction(new ConditionalGotoInstruction<V>(null, false, right));
      push(myFactory.getConstFactory().getFalse());
      addInstruction(new GotoInstruction<V>(myFlow.getEndOffset(expression)));
      gotoSuccess.setOffset(myFlow.getNextOffset());
      push(myFactory.getConstFactory().getTrue());
    }
    else {
      final GroovyResolveResult[] resolveResults = expression.multiResolve(false);
      myExpressionHelper.binaryOperation(expression, left, right, operatorToken, resolveResults);
    }

    finishElement(expression);
  }

  @Override
  public void visitInstanceofExpression(GrInstanceOfExpression expression) {
    startElement(expression);

    GrExpression operand = expression.getOperand();
    GrTypeElement typeElement = expression.getTypeElement();
    if (typeElement == null) {
      pushUnknown();
    }
    else {
      operand.accept(this);
      PsiType type = typeElement.getType();
      addInstruction(new PushInstruction<V>(myFactory.createTypeValue(type, Nullness.NOT_NULL), expression));
      addInstruction(new GrInstanceofInstruction<V>(operand, type));
    }

    finishElement(expression);
  }

  @Override
  public void visitClosure(GrClosableBlock closure) {
    push(myFactory.createValue(closure));
  }

  @Override
  public void visitLabeledStatement(GrLabeledStatement labeledStatement) {
    startElement(labeledStatement);
    super.visitLabeledStatement(labeledStatement);
    finishElement(labeledStatement);
  }


  @Override
  public void visitReturnStatement(GrReturnStatement returnStatement) {
    startElement(returnStatement);

    final GrExpression returnValue = returnStatement.getReturnValue();
    if (returnValue != null) {
      returnValue.accept(this);
    }
    else {
      pushUnknown();
    }
    pop();

    finishElement(returnStatement);
  }

  @Override
  public void visitLiteralExpression(GrLiteral literal) {
    startElement(literal);

    DfaValue dfaValue = myFactory.createLiteralValue(literal);
    push(dfaValue, literal);

    finishElement(literal);
  }

  @Override
  public void visitRangeExpression(GrRangeExpression range) {
    startElement(range);

    final GrExpression leftOperand = range.getLeftOperand();
    leftOperand.accept(this);

    final GrExpression rightOperand = range.getRightOperand();
    if (rightOperand != null) {
      rightOperand.accept(this);
    }
    else {
      pushUnknown();
    }

    addInstruction(new GrRangeInstruction<V>(range));
    finishElement(range);
  }

  @Override
  public void visitListOrMap(GrListOrMap listOrMap) {
    startElement(listOrMap);

    if (listOrMap.isMap()) {
      for (GrNamedArgument namedArgument : listOrMap.getNamedArguments()) {
        final GrExpression expression = namedArgument.getExpression();
        if (expression != null) {
          expression.accept(this);
          pop();
        }
      }
    }
    else {
      for (GrExpression expression : listOrMap.getInitializers()) {
        expression.accept(this);
        pop();
      }
    }

    push(myFactory.createValue(listOrMap));

    finishElement(listOrMap);
  }

  @Override
  public void visitIndexProperty(GrIndexProperty expression) {
    startElement(expression);
    myCallHelper.processIndexProperty(expression, myCallHelper.EMPTY);
    finishElement(expression);
  }

  private void startElement(PsiElement element) {
    myFlow.startElement(element);
    myElementStack.push(element);
  }

  private void finishElement(GroovyPsiElement element) {
    myFlow.finishElement(element);
    PsiElement popped = myElementStack.pop();
    if (element != popped) {
      throw new AssertionError("Expected " + element + ", popped " + popped);
    }
    if (shouldCheckReturn(element)) {
      addInstruction(new CheckReturnValueInstruction<V>(
        element instanceof GrReturnStatement
        ? ((GrReturnStatement)element).getReturnValue()
        : element
      ));
      //addInstruction(new ReturnInstruction<V>(false, element));
    }
    if (element instanceof GrStatement && element.getParent() instanceof GrStatementOwner) {
      if (element instanceof GrExpression) {
        pop();
      }
      addInstruction(new FinishElementInstruction(element));
    }
  }

  void flushCodeBlockVariables(GrOpenBlock block) {
    for (GrStatement statement : block.getStatements()) {
      if (statement instanceof GrVariableDeclaration) {
        for (GrVariable variable : ((GrVariableDeclaration)statement).getVariables()) {
          removeVariable(variable);
        }
      }
    }
  }

  private void removeVariable(@Nullable GrVariable variable) {
    if (variable == null) return;
    addInstruction(new FlushVariableInstruction<V>(myFactory.getVarFactory().createVariableValue(variable, false)));
  }

  <T extends Instruction<V>> T addInstruction(T instruction) {
    myFlow.addInstruction(instruction);
    return instruction;
  }

  PopInstruction pop() {
    return addInstruction(new PopInstruction());
  }

  PushInstruction push(DfaValue value, PsiElement place) {
    return addInstruction(new PushInstruction(value, place));
  }

  PushInstruction push(DfaValue value, PsiElement place, boolean writing) {
    return addInstruction(new PushInstruction<V>(value, place, writing));
  }

  PushInstruction push(DfaValue value) {
    return push(value, null);
  }

  void pushUnknown() {
    push(DfaUnknownValue.getInstance());
  }

  void pushNull() {
    push(myFactory.getConstFactory().getNull());
  }

  private void coerceToBoolean() {
    addInstruction(new GrCoerceToBooleanInstruction<V>());
  }
}
