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
import com.intellij.psi.util.PsiTreeUtil;
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
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.arithmetic.GrRangeExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.List;

import static org.jetbrains.plugins.groovy.lang.flow.GrCFExpressionHelper.shouldCheckReturn;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.*;
import static org.jetbrains.plugins.groovy.lang.lexer.TokenSets.ASSIGNMENTS_TO_OPERATORS;

public class GrControlFlowAnalyzerImpl<V extends GrInstructionVisitor<V>>
  extends GroovyRecursiveElementVisitor implements IControlFlowAnalyzer<V> {

  final GrDfaValueFactory factory;
  final PsiElement codeFragment;
  final Stack<PsiElement> elementStack = new Stack<PsiElement>();
  final ControlFlowImpl<V> flow = new ControlFlowImpl<V>();

  final GrCFExpressionHelper<V> expressionHelper;
  final GrCFExceptionHelper<V> exceptionHelper;
  final GrCFCallHelper<V> callHelper;

  public GrControlFlowAnalyzerImpl(@NotNull GrDfaValueFactory factory, @NotNull PsiElement block) {
    this.factory = factory;
    codeFragment = block;
    expressionHelper = new GrCFExpressionHelper<V>(this);
    exceptionHelper = new GrCFExceptionHelper<V>(this);
    callHelper = new GrCFCallHelper<V>(this);
  }

  @Override
  public ControlFlow<V> buildControlFlow() {
    try {
      codeFragment.accept(new GroovyPsiElementVisitor(this) {
        @Override
        public void visitErrorElement(PsiErrorElement element) {
          throw new CannotAnalyzeException();
        }
      });
      //if (flow.getInstructionCount() == 0) {
      flow.addInstruction(new ReturnInstruction<V>(false, null));
      //}
      return flow;
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
  public void visitMethod(GrMethod method) {
    startElement(method);
    super.visitMethod(method);
    finishElement(method);
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
          expressionHelper.initialize(variables[i], initializers[i]);
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
          expressionHelper.initialize(variable, initializer);
          pop();
        }
      }
    }

    finishElement(variableDeclaration);
  }

  @Override
  public void visitAssignmentExpression(final GrAssignmentExpression expression) {
    final GrExpression right = expression.getRValue();
    if (right == null) {
      startElement(expression);
      pushUnknown();
      finishElement(expression);
      return;
    }
    final GrExpression left = expression.getLValue();
    final IElementType op = expression.getOperationTokenType();
    if (op == mASSIGN) {
      startElement(expression);
      if (left instanceof GrTupleExpression) {
        expressionHelper.assignTuple(((GrTupleExpression)left).getExpressions(), right);
        pushUnknown(); // so there will be value to pop in finishElement()
      }
      else {
        expressionHelper.assign(left, right);
      }
      finishElement(expression);
    }
    else {
      expressionHelper.assign(left, right, callHelper.new Arguments() {
        @Override
        public int runArguments() {
          expressionHelper.binaryOperation(expression, left, right, ASSIGNMENTS_TO_OPERATORS.get(op), expression.multiResolve(false));
          return 1;
        }
      });
    }
  }

  @Override
  public void visitMethodCallExpression(GrMethodCallExpression methodCallExpression) {
    startElement(methodCallExpression);
    callHelper.processMethodCall(methodCallExpression);
    finishElement(methodCallExpression);
  }

  @Override
  public void visitApplicationStatement(GrApplicationStatement applicationStatement) {
    startElement(applicationStatement);
    callHelper.processMethodCall(applicationStatement);
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
        expressionHelper.boxUnbox(PsiType.INT, dimension.getType());
        pop();
      }
      exceptionHelper.addConditionalRuntimeThrow();
      addInstruction(new GrMethodCallInstruction(expression, null));
    }
    else {
      final PsiMethod ctr = expression.resolveMethod();
      callHelper.visitArguments(expression);
      final GrAnonymousClassDefinition definition = expression.getAnonymousClassDefinition();
      if (definition != null) {
        definition.accept(this);
      }
      exceptionHelper.addConditionalRuntimeThrow();
      addInstruction(new GrMethodCallInstruction(expression, null));
      if (!exceptionHelper.catchStack.isEmpty()) {
        exceptionHelper.addMethodThrows(ctr, expression);
      }
    }

    finishElement(expression);
  }

  @Override
  public void visitAnonymousClassDefinition(GrAnonymousClassDefinition definition) {
    startElement(definition);

    final GrTypeDefinitionBody body = definition.getBody();
    for (GrMethod method : body == null ? GrMethod.EMPTY_ARRAY : body.getMethods()) {
      final GrOpenBlock methodBlock = method.getBlock();
      if (methodBlock == null) continue;
      pushUnknown();
      addInstruction(new ConditionalGotoInstruction<V>(flow.getEndOffset(methodBlock), false, null));
      startElement(method);
      methodBlock.accept(this);
      finishElement(method);
      addInstruction(new GotoInstruction<V>(flow.getEndOffset(definition)));
    }

    finishElement(definition);
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
                                            ? flow.getStartOffset(elseBranch)
                                            : flow.getEndOffset(statement);

    if (condition != null) {
      condition.accept(this);
      coerceToBoolean(condition);
      addInstruction(new ConditionalGotoInstruction(ifFalseOffset, true, condition));
    }

    if (thenBranch != null) {
      thenBranch.accept(this);
    }

    if (elseBranch != null) {
      addInstruction(new GotoInstruction(flow.getEndOffset(statement)));
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
      final ControlFlowOffset statementsBlockOffset = flow.getNextOffset();
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
      fallbackGoto.setOffset(flow.getEndOffset(switchStatement));
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

      // caseValue.isCase(switchValue)
      processCaseCall(condition, caseLabel);

      if (i == labels.length - 1) {
        // if not matched then go to next case section
        // if matched then next instruction is the start of the statements block
        addInstruction(new ConditionalGotoInstruction<V>(
          flow.getEndOffset(section),
          true,
          caseLabel.getValue()
        ));
      }
      else {
        // if matched go to the statements block
        // if not matched then next instruction is the start of the next case label
        result.add(addInstruction(new ConditionalGotoInstruction<V>(
          null,
          false,
          caseLabel.getValue()
        )));
      }

      finishElement(caseLabel);
    }
    return result;
  }

  private void processCaseCall(@NotNull GrExpression condition, @NotNull GrCaseLabel caseLabel) {
    final GrExpression caseValue = caseLabel.getValue();
    if (caseValue == null) {
      pushUnknown();
    }
    else if (caseValue instanceof GrReferenceExpression && ((GrReferenceExpression)caseValue).resolve() instanceof PsiClass) {
      addInstruction(new DupInstruction<V>());    // switch value
      caseValue.accept(this);                     // case value
      addInstruction(new BinopInstruction<V>(DfaRelation.INSTANCEOF, caseValue));
    }
    else if (caseValue instanceof GrLiteral) {
      addInstruction(new DupInstruction<V>());    // switch value
      caseValue.accept(this);                     // case value 
      addInstruction(new BinopInstruction<V>(DfaRelation.EQ, caseValue));
    }
    else {
      final GroovyResolveResult[] cases = caseLabel.multiResolve(false);
      if (cases.length == 1) {
        callHelper.processRegularCall(caseLabel, caseValue, cases[0], condition);
      }
      else {
        pushUnknown();
      }
    }
  }

  @Override
  public void visitBreakStatement(GrBreakStatement statement) {
    final GrStatement targetStatement = statement.findTargetStatement();
    if (targetStatement != null) {
      addInstruction(new GotoInstruction<V>(flow.getEndOffset(targetStatement)));
    }
  }

  @Override
  public void visitContinueStatement(GrContinueStatement statement) {
    final GrStatement targetStatement = statement.findTargetStatement();
    if (targetStatement != null) {
      addInstruction(new GotoInstruction<V>(flow.getStartOffset(targetStatement)));
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
      addInstruction(new ConditionalGotoInstruction<V>(flow.getEndOffset(statement), true, condition));

      final GrStatement body = statement.getBody();
      if (body != null) {
        body.accept(this);
      }
      final GrExpression update = traditionalForClause.getUpdate();
      if (update != null) {
        update.accept(this);
        pop();
      }
      addInstruction(new GotoInstruction<V>(flow.getStartOffset(condition)));
    }
    else if (clause instanceof GrForInClause) {
      final GrForInClause forInClause = (GrForInClause)clause;

      final GrExpression iteratedValue = forInClause.getIteratedExpression();
      if (iteratedValue != null) {
        iteratedValue.accept(this);
        addInstruction(new GrDereferenceInstruction<V>(iteratedValue));
      }

      final ControlFlowImpl.ControlFlowOffset loopStartOffset = flow.getNextOffset();
      removeVariable(parameter);

      pushUnknown();
      addInstruction(new ConditionalGotoInstruction(flow.getEndOffset(statement), true, null));

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
    addInstruction(new ConditionalGotoInstruction<V>(flow.getEndOffset(whileStatement), true, condition));

    final GrStatement body = whileStatement.getBody();
    if (body != null) {
      body.accept(this);
    }
    addInstruction(new GotoInstruction<V>(flow.getStartOffset(whileStatement)));

    finishElement(whileStatement);
  }

  @Override
  public void visitTryStatement(GrTryCatchStatement statement) {
    startElement(statement);

    GrOpenBlock tryBlock = statement.getTryBlock();
    GrFinallyClause finallyBlock = statement.getFinallyClause();

    if (finallyBlock != null) {
      exceptionHelper.catchStack.push(new CatchDescriptor(finallyBlock));
    }

    GrCatchClause[] sections = statement.getCatchClauses();
    for (int i = sections.length - 1; i >= 0; i--) {
      GrCatchClause section = sections[i];
      GrOpenBlock catchBlock = section.getBody();
      PsiParameter parameter = section.getParameter();
      if (parameter != null && catchBlock != null) {
        PsiType type = parameter.getType();
        if (type instanceof PsiClassType || type instanceof PsiDisjunctionType) {
          exceptionHelper.catchStack.push(new CatchDescriptor(parameter, catchBlock));
          continue;
        }
      }
      throw new CannotAnalyzeException();
    }

    final ControlFlowImpl.ControlFlowOffset endOffset = finallyBlock == null
                                                        ? flow.getEndOffset(statement)
                                                        : flow.getStartOffset(finallyBlock);

    tryBlock.accept(this);
    addInstruction(new GotoInstruction(endOffset));

    for (GrCatchClause section : sections) {
      section.accept(this);
      addInstruction(new GotoInstruction(endOffset));
      exceptionHelper.catchStack.pop();
    }

    if (finallyBlock != null) {
      CatchDescriptor finallyDescriptor = exceptionHelper.catchStack.pop();
      finallyBlock.accept(this);

      //if $exception$==null => continue normal execution
      addInstruction(new PushInstruction(exceptionHelper.getExceptionHolder(finallyDescriptor), null));
      addInstruction(new PushInstruction(factory.getConstFactory().getNull(), null));
      addInstruction(new BinopInstruction(DfaRelation.EQ, null, statement.getProject()));
      addInstruction(new ConditionalGotoInstruction(flow.getEndOffset(statement), false, null));

      // else throw $exception$
      exceptionHelper.rethrowException(finallyDescriptor, false);
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
    final DfaVariableValue exceptionHolder = exceptionHelper.getExceptionHolder(currentDescriptor);

    // exception is in exceptionHolder mock variable
    // check if it's assignable to catch parameter type
    PsiType declaredType = catchClauseParameter.getType();
    List<PsiType> flattened = declaredType instanceof PsiDisjunctionType ?
                              ((PsiDisjunctionType)declaredType).getDisjunctions() :
                              ContainerUtil.createMaybeSingletonList(declaredType);
    for (PsiType catchType : flattened) {
      addInstruction(new PushInstruction(exceptionHolder, null));
      addInstruction(new PushInstruction(factory.createTypeValue(catchType, Nullness.UNKNOWN), null));
      addInstruction(new BinopInstruction(DfaRelation.INSTANCEOF, null, catchClause.getProject()));
      addInstruction(new ConditionalGotoInstruction(ControlFlowImpl.deltaOffset(flow.getStartOffset(catchBlock), -5), false, null));
    }

    // not assignable => rethrow 
    exceptionHelper.rethrowException(currentDescriptor, true);

    // e = $exception$
    addInstruction(new PushInstruction(factory.getVarFactory().createVariableValue(catchClauseParameter, false), null));
    addInstruction(new PushInstruction(exceptionHolder, null));
    addInstruction(new GrAssignInstruction<V>());
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
      coerceToBoolean(condition);
      addInstruction(new ConditionalGotoInstruction(flow.getEndOffset(assertStatement), false, condition));
      if (description != null) {
        description.accept(this);
      }

      CatchDescriptor cd = exceptionHelper.findNextCatch(false);
      exceptionHelper.initException(exceptionHelper.assertionError, cd);
      exceptionHelper.addThrowCode(cd, assertStatement);
    }
    finishElement(assertStatement);
  }

  @Override
  public void visitThrowStatement(GrThrowStatement statement) {
    final GrExpression exception = statement.getException();
    if (exception == null) {
      return;
    }

    startElement(statement);

    exception.accept(this);
    CatchDescriptor cd = exceptionHelper.findNextCatch(false);
    if (cd == null) {
      addInstruction(new ReturnInstruction(true, statement));
      finishElement(statement);
      return;
    }

    exceptionHelper.addConditionalRuntimeThrow();
    addInstruction(new DupInstruction());
    addInstruction(new PushInstruction(factory.getConstFactory().getNull(), null));
    addInstruction(new BinopInstruction(DfaRelation.EQ, null, statement.getProject()));
    ConditionalGotoInstruction gotoInstruction = new ConditionalGotoInstruction(null, true, null);
    addInstruction(gotoInstruction);

    addInstruction(new PopInstruction());
    exceptionHelper.initException(exceptionHelper.npe, cd);
    exceptionHelper.addThrowCode(cd, statement);

    gotoInstruction.setOffset(flow.getInstructionCount());
    addInstruction(new PushInstruction(exceptionHelper.getExceptionHolder(cd), null));
    addInstruction(new SwapInstruction());
    addInstruction(new GrAssignInstruction<V>());
    addInstruction(new PopInstruction());
    exceptionHelper.addThrowCode(cd, statement);

    finishElement(statement);
  }

  @Override
  public void visitParameterList(GrParameterList parameterList) {
    startElement(parameterList);
    for (GrParameter parameter : parameterList.getParameters()) {
      parameter.accept(this);
      pop();
    }
    finishElement(parameterList);
  }

  @Override
  public void visitParameter(GrParameter parameter) {
    startElement(parameter);
    final GrExpression initializer = parameter.getInitializerGroovy();
    if (initializer != null) {
      pushUnknown();
      final ConditionalGotoInstruction<V> ifInitialized = addInstruction(new ConditionalGotoInstruction<V>(null, false, null));
      pushUnknown();
      final GotoInstruction<V> toEnd = addInstruction(new GotoInstruction<V>(null));
      ifInitialized.setOffset(flow.getNextOffset());
      expressionHelper.initialize(parameter, initializer);
      toEnd.setOffset(flow.getNextOffset());
    }
    else {
      pushUnknown();
    }
    finishElement(parameter);
  }

  @Override
  public void visitElvisExpression(GrElvisExpression expression) {
    startElement(expression);

    final GrExpression condition = expression.getCondition();
    condition.accept(this);
    coerceToBoolean(condition);
    addInstruction(new DupInstruction<V>());
    addInstruction(new ConditionalGotoInstruction<V>(flow.getEndOffset(expression), false, condition));
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
    coerceToBoolean(condition);
    final ConditionalGotoInstruction<V> gotoElse = addInstruction(new ConditionalGotoInstruction<V>(null, true, condition));

    if (thenBranch == null) {
      pushUnknown();
    }
    else {
      thenBranch.accept(this);
    }
    addInstruction(new GotoInstruction<V>(flow.getEndOffset(expression)));

    gotoElse.setOffset(flow.getNextOffset());
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
      push(factory.createValue(referenceExpression), referenceExpression, writing);
    }
    else {
      qualifierExpression.accept(this);
      final IElementType dot = referenceExpression.getDotTokenType();
      if (dot == mOPTIONAL_DOT || dot == mSPREAD_DOT) {
        addInstruction(new DupInstruction<V>()); // save qualifier for later use
        pushNull();
        addInstruction(new BinopInstruction(DfaRelation.NE, referenceExpression));
        final ConditionalGotoInstruction gotoToNull = addInstruction(new ConditionalGotoInstruction(null, true, qualifierExpression));

        // not null branch
        // qualifier is on top of stack
        expressionHelper.dereference(qualifierExpression, referenceExpression, writing);
        final GotoInstruction<V> gotoEnd = addInstruction(new GotoInstruction<V>(null));
        gotoToNull.setOffset(flow.getNextOffset());

        // null branch
        pop();        // pop duplicated qualifier
        pushNull();
        gotoEnd.setOffset(flow.getNextOffset());
      }
      else {
        expressionHelper.dereference(qualifierExpression, referenceExpression, writing);
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
      final IElementType tokenType = expression.getOperationTokenType();
      if (tokenType == mLNOT) {
        operand.accept(this);
        coerceToBoolean(operand);
        addInstruction(new NotInstruction<V>());
      }
      else if (tokenType == mINC || tokenType == mDEC) {
        operand.accept(this);
        if (expression.isPostfix()) {
          expressionHelper.delay(expression);
        }
        else {
          expressionHelper.processIncrementDecrement(expression);
        }
      }
      else {
        final GroovyResolveResult[] results = expression.multiResolve(false);
        callHelper.processRegularCall(
          expression, operand, results.length == 1 ? results[0] : GroovyResolveResult.EMPTY_RESULT
        );
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
      coerceToBoolean(left);
      addInstruction(new DupInstruction<V>());
      addInstruction(new ConditionalGotoInstruction<V>(flow.getEndOffset(expression), isAnd, left));
      pop();
      right.accept(this);

      coerceToBoolean(right);
      final ConditionalGotoInstruction<V> gotoSuccess = addInstruction(new ConditionalGotoInstruction<V>(null, false, right));
      push(factory.getConstFactory().getFalse());
      addInstruction(new GotoInstruction<V>(flow.getEndOffset(expression)));
      gotoSuccess.setOffset(flow.getNextOffset());
      push(factory.getConstFactory().getTrue());
    }
    else {
      final GroovyResolveResult[] resolveResults = expression.multiResolve(false);
      expressionHelper.binaryOperation(expression, left, right, operatorToken, resolveResults);
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
      addInstruction(new PushInstruction<V>(factory.createTypeValue(type, Nullness.NOT_NULL), expression));
      addInstruction(new GrInstanceofInstruction<V>(operand, type));
    }

    finishElement(expression);
  }

  @Override
  public void visitSafeCastExpression(final GrSafeCastExpression typeCastExpression) {
    startElement(typeCastExpression);

    typeCastExpression.getOperand().accept(this);
    final GrTypeElement typeElement = typeCastExpression.getCastTypeElement();
    if (typeElement == null) {
      pop();
      pushUnknown();
    }
    else {
      final PsiType type = typeElement.getType();
      if (type == PsiType.BOOLEAN ||
          type.equalsToText(CommonClassNames.JAVA_LANG_BOOLEAN) ||
          type.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
        pop();
        push(factory.createTypeValue(type, Nullness.NOT_NULL));
      }
      else {
        pushNull();
        addInstruction(new BinopInstruction<V>(DfaRelation.EQ, null, typeCastExpression.getProject()));
        final ConditionalGotoInstruction<V> ifNotNull = addInstruction(new ConditionalGotoInstruction<V>(null, true, null));

        // if operand is null
        pushNull();
        addInstruction(new GotoInstruction<V>(flow.getEndOffset(typeCastExpression)));

        // if operand is not null
        ifNotNull.setOffset(flow.getNextOffset());
        push(factory.createTypeValue(type, Nullness.NOT_NULL));
      }
    }
    finishElement(typeCastExpression);
  }

  @Override
  public void visitCastExpression(GrTypeCastExpression typeCastExpression) {
    startElement(typeCastExpression);

    final GrExpression operand = typeCastExpression.getOperand();
    if (operand == null) {
      push(factory.createTypeValue(typeCastExpression.getType(), Nullness.NOT_NULL));
    }
    else {
      operand.accept(this);
      addInstruction(new GrTypeCastInstruction<V>(typeCastExpression.getCastTypeElement().getType(), typeCastExpression));
    }

    finishElement(typeCastExpression);
  }

  @Override
  public void visitClosure(GrClosableBlock closure) {
    startElement(closure);
    processClosure(false, closure);
    finishElement(closure);
  }

  private void processClosure(boolean definitelyExecutes, GrClosableBlock closure) {
    push(factory.createValue(closure));
    if (!definitelyExecutes) {
      pushUnknown();
      addInstruction(new ConditionalGotoInstruction<V>(flow.getEndOffset(closure), false, null));
    }
    for (GrStatement statement : closure.getStatements()) {
      statement.accept(this);
    }
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

    finishElement(returnStatement);
  }

  @Override
  public void visitLiteralExpression(GrLiteral literal) {
    startElement(literal);

    DfaValue dfaValue = factory.createLiteralValue(literal);
    push(dfaValue, literal);

    finishElement(literal);
  }

  @Override
  public void visitGStringExpression(GrString gstring) {
    startElement(gstring);

    for (GrStringInjection injection : gstring.getInjections()) {
      final GrClosableBlock closableBlock = injection.getClosableBlock();
      if (closableBlock != null) {
        processClosure(true, closableBlock);
        pop();
      }
      final GrExpression expression = injection.getExpression();
      if (expression != null) {
        expression.accept(this);
        pop();
      }
    }
    push(factory.createLiteralValue(gstring), gstring);

    finishElement(gstring);
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
        expressionHelper.processDelayed();
      }
    }

    push(factory.createValue(listOrMap));

    finishElement(listOrMap);
  }

  @Override
  public void visitIndexProperty(GrIndexProperty expression) {
    startElement(expression);
    callHelper.processIndexProperty(expression, callHelper.EMPTY);
    finishElement(expression);
  }

  private void startElement(PsiElement element) {
    flow.startElement(element);
    elementStack.push(element);
  }

  private void finishElement(GroovyPsiElement element) {
    flow.finishElement(element);
    PsiElement popped = elementStack.pop();
    if (element != popped) {
      throw new AssertionError("Expected " + element + ", popped " + popped);
    }
    if (shouldCheckReturn(element)) {
      final PsiElement containingMethod = PsiTreeUtil.getParentOfType(element, GrMethod.class, GrClosableBlock.class);
      if (containingMethod instanceof GrMethod) {
        expressionHelper.boxUnbox(((GrMethod)containingMethod).getReturnType(), ((GrExpression)element).getType());
      }
      addInstruction(new CheckReturnValueInstruction<V>(element));
      expressionHelper.processDelayed();
      exceptionHelper.returnCheckingFinally(false, element);
    }
    else if (element instanceof GrStatement && element.getParent() instanceof GrStatementOwner) {
      if (element instanceof GrExpression && !(element instanceof GrConditionalExpression)) {
        pop();
      }
      expressionHelper.processDelayed();
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
    addInstruction(new FlushVariableInstruction<V>(factory.getVarFactory().createVariableValue(variable, false)));
  }

  <T extends Instruction<V>> T addInstruction(T instruction) {
    flow.addInstruction(instruction);
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
    push(factory.getConstFactory().getNull());
  }

  private void coerceToBoolean(@NotNull GrExpression expression) {
    final PsiType type = expression.getType();
    if (type != null) {
      final GroovyResolveResult[] results = ResolveUtil.getMethodCandidates(type, "asBoolean", expression);
      if (results.length == 1) {
        final GroovyResolveResult result = results[0];
        final PsiElement element = result.getElement();
        if (element instanceof GrGdkMethod && PsiImplUtil.isFromDGM((GrGdkMethod)element)) {
          // it means no overloads found
          addInstruction(new GrCoerceToBooleanInstruction<V>());
        }
        else {
          // compare top of stack with null
          addInstruction(new DupInstruction<V>());
          pushNull();
          addInstruction(new BinopInstruction<V>(DfaRelation.EQ, null, expression.getProject()));

          final ConditionalGotoInstruction<V> gotoNotNull = addInstruction(new ConditionalGotoInstruction<V>(null, true, null));
          // null coerces to false always 
          pop();
          push(factory.getConstFactory().getFalse());
          final GotoInstruction<V> gotoEnd = addInstruction(new GotoInstruction<V>(null));

          // found some non-DGM method, call it, qualifier is expression itself and already on top of stack
          gotoNotNull.setOffset(flow.getNextOffset());
          addInstruction(new GrMethodCallInstruction<V>(expression, GrExpression.EMPTY_ARRAY, result));
          gotoEnd.setOffset(flow.getNextOffset());
        }
      }
      else if (results.length > 1) {
        // do not know what to do
        pop();
        pushUnknown();
      }
      else {
        addInstruction(new GrCoerceToBooleanInstruction<V>());
      }
    }
    else {
      addInstruction(new GrCoerceToBooleanInstruction<V>());
    }
  }
}
