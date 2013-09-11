/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.convertToJava;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.noReturnMethod.MissingReturnInspection;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrCondition;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrAssertStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrFlowInterruptingStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrThrowStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForInClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrTraditionalForClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrApplicationStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

import java.util.Collection;
import java.util.Set;

import static org.jetbrains.plugins.groovy.refactoring.convertToJava.GenerationUtil.*;
import static org.jetbrains.plugins.groovy.refactoring.convertToJava.TypeWriter.writeType;

/**
 * @author Maxim.Medvedev
 */
public class CodeBlockGenerator extends Generator {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.refactoring.convertToJava.CodeBlockGenerator");

  private static final boolean IN_TEST = ApplicationManager.getApplication().isUnitTestMode();

  private final StringBuilder builder;
  private final ExpressionContext context;

  private Set<GrStatement> myExitPoints;

  public CodeBlockGenerator(StringBuilder builder, ExpressionContext context) {
    this(builder, context, null);
  }

  public CodeBlockGenerator(StringBuilder builder, ExpressionContext context, @Nullable Collection<GrStatement> exitPoints) {
    this.builder = builder;
    this.context = context;
    myExitPoints = new HashSet<GrStatement>();
    if (exitPoints != null) {
      myExitPoints.addAll(exitPoints);
    }
  }

  @Override
  public StringBuilder getBuilder() {
    return builder;
  }

  @Override
  public ExpressionContext getContext() {
    return context;
  }

  public void generateMethodBody(GrMethod method) {
    final GrOpenBlock block = method.getBlock();

    boolean shouldInsertReturnNull = false;
    myExitPoints.clear();
    PsiType returnType = context.typeProvider.getReturnType(method);
    if (!method.isConstructor() && returnType != PsiType.VOID) {
      myExitPoints.addAll(ControlFlowUtils.collectReturns(block));
      shouldInsertReturnNull = block != null && !(returnType instanceof PsiPrimitiveType) &&
                               MissingReturnInspection.methodMissesSomeReturns(block,
                                                                               MissingReturnInspection.ReturnStatus.getReturnStatus(method));
    }

    if (block != null) {
      generateCodeBlock(block, shouldInsertReturnNull);
    }
  }

  @Override
  public void visitMethod(GrMethod method) {
    LOG.error("don't invoke it!!!");
  }

  @Override
  public void visitOpenBlock(GrOpenBlock block) {
    generateCodeBlock(block, false);
  }

  public void generateCodeBlock(GrCodeBlock block, boolean shouldInsertReturnNull) {
    builder.append("{");
    GrParameter[] parameters;
    if (block.getParent() instanceof GrMethod) {
      GrMethod method = (GrMethod)block.getParent();
      parameters = method.getParameters();
    }
    else if (block instanceof GrClosableBlock) {
      parameters = ((GrClosableBlock)block).getAllParameters();
    }
    else {
      parameters = GrParameter.EMPTY_ARRAY;
    }

    for (GrParameter parameter : parameters) {
      if (context.analyzedVars.toWrap(parameter)) {
        StringBuilder typeText = new StringBuilder().append(GroovyCommonClassNames.GROOVY_LANG_REFERENCE);
        writeTypeParameters(typeText, new PsiType[]{context.typeProvider.getParameterType(parameter)}, parameter,
                            new GeneratorClassNameProvider());
        builder.append("final ").append(typeText).append(' ').append(context.analyzedVars.toVarName(parameter))
          .append(" = new ").append(typeText).append('(').append(parameter.getName()).append(");\n");
      }
    }
    visitStatementOwner(block, shouldInsertReturnNull);
    builder.append("}\n");
  }

  public void visitStatementOwner(GrStatementOwner owner, boolean shouldInsertReturnNull) {
    boolean hasLineFeed = false;
    for (PsiElement e = owner.getFirstChild(); e != null; e = e.getNextSibling()) {
      if (e instanceof GrStatement) {
        ((GrStatement)e).accept(this);
        hasLineFeed = false;
      }
      else if (TokenSets.COMMENT_SET.contains(e.getNode().getElementType())) {
        builder.append(e.getText());
      }
      else if (org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.isLineFeed(e)) {
        hasLineFeed = true;
        if (IN_TEST) {
          builder.append(genSameLineFeed(e.getText()));
        }
        else {
          builder.append(e.getText());
        }
      }
    }
    if (shouldInsertReturnNull) {
      if (!hasLineFeed) {
        builder.append('\n');
      }
      builder.append("return null;\n");
    }
  }

  private static String genSameLineFeed(String text) {
    final int count = StringUtil.countChars(text, '\n');
    return StringUtil.repeatSymbol('\n', count);
  }

  @Override
  public void visitConstructorInvocation(final GrConstructorInvocation invocation) {
    GenerationUtil.writeStatement(builder, context, invocation, new StatementWriter() {
      @Override
      public void writeStatement(StringBuilder builder, ExpressionContext context) {
        final GrReferenceExpression thisOrSuperKeyword = invocation.getInvokedExpression();
        final GrArgumentList argumentList = invocation.getArgumentList();
        final GroovyResolveResult resolveResult = invocation.advancedResolve();
        if (thisOrSuperKeyword.getQualifier() == null) {
          builder.append(thisOrSuperKeyword.getReferenceName());
        }
        else {
          thisOrSuperKeyword.accept(new ExpressionGenerator(builder, context));
        }
        new ArgumentListGenerator(builder, context).generate(
          GrClosureSignatureUtil.createSignature(resolveResult),
          argumentList.getExpressionArguments(),
          argumentList.getNamedArguments(),
          invocation.getClosureArguments(),
          invocation
        );

        builder.append(';');
      }
    });

  }

  public void visitStatement(GrStatement statement) {
    LOG.error("all statements must be overloaded");
  }

  @Override
  public void visitFlowInterruptStatement(GrFlowInterruptingStatement statement) {
    builder.append(statement.getStatementText());
    final String name = statement.getLabelName();
    if (name != null) {
      builder.append(' ').append(name); //todo check incorrect labels
    }
    builder.append(';');
  }

  @Override
  public void visitReturnStatement(final GrReturnStatement returnStatement) {
    final GrExpression returnValue = returnStatement.getReturnValue();
    if (returnValue == null) {
      builder.append("return;\n");
      return;
    }

    GenerationUtil.writeStatement(builder, context, returnStatement, new StatementWriter() {
      @Override
      public void writeStatement(StringBuilder builder, ExpressionContext context) {
        writeReturn(builder, context, returnValue);
      }
    });
  }

  private void writeStatement(StringBuilder statementBuilder,
                              GrStatement statement,
                              @Nullable ExpressionContext context) {
    GenerationUtil.writeStatement(builder, statementBuilder, statement, context);
  }

  @Override
  public void visitAssertStatement(final GrAssertStatement assertStatement) {
    final GrExpression assertion = assertStatement.getAssertion();
    final GrExpression message = assertStatement.getErrorMessage();
    if (assertion != null) {
      GenerationUtil.writeStatement(builder, context, assertStatement, new StatementWriter() {
        @Override
        public void writeStatement(StringBuilder builder, ExpressionContext context) {
          builder.append("assert ");
          assertion.accept(new ExpressionGenerator(builder, context));
          if (message != null) {
            builder.append(" : ");
            message.accept(new ExpressionGenerator(builder, context));
          }
          builder.append(';');
        }
      });
    }
    else if (message != null) {
      GenerationUtil.writeStatement(builder, context, assertStatement, new StatementWriter() {
        @Override
        public void writeStatement(StringBuilder builder, ExpressionContext context) {
          builder.append("assert : ");
          message.accept(new ExpressionGenerator(builder, context));
          builder.append(';');
        }
      });
    }
    else {
      builder.append("assert;");
    }
  }

  @Override
  public void visitThrowStatement(GrThrowStatement throwStatement) {
    final GrExpression exception = throwStatement.getException();
    if (exception == null) {
      builder.append("throw ;");
      return;
    }
    GenerationUtil.writeStatement(builder, context, throwStatement, new StatementWriter() {
      @Override
      public void writeStatement(StringBuilder builder, ExpressionContext context) {
        builder.append("throw ");
        exception.accept(new ExpressionGenerator(builder, context));                     //todo add exception to method 'throws' list
        builder.append(';');
      }
    });
  }

  @Override
  public void visitLabeledStatement(final GrLabeledStatement labeledStatement) {
    GenerationUtil.writeStatement(builder, context, labeledStatement, new StatementWriter() {
      @Override
      public void writeStatement(StringBuilder builder, ExpressionContext context) {
        final String label = labeledStatement.getName();
        final GrStatement statement = labeledStatement.getStatement();

        builder.append(label).append(": ");
        if (statement != null) {
          statement.accept(new CodeBlockGenerator(builder, context, myExitPoints));
        }
      }
    });
  }

  @Override
  public void visitExpression(final GrExpression expression) {
    GenerationUtil.writeStatement(builder, context, expression, new StatementWriter() {
      @Override
      public void writeStatement(StringBuilder builder, ExpressionContext context) {
        if (myExitPoints.contains(expression) && expression.getType() != PsiType.VOID) {
          writeReturn(builder, context, expression);
        }
        else {
          expression.accept(new ExpressionGenerator(builder, context));
          builder.append(';');
        }
      }
    });
  }

  private static void writeReturn(StringBuilder builder, ExpressionContext context, final GrExpression expression) {
    builder.append("return ");

    final PsiType expectedReturnType = PsiImplUtil.inferReturnType(expression);
    final PsiType nnReturnType =
      expectedReturnType == null || expectedReturnType == PsiType.VOID ? TypesUtil.getJavaLangObject(expression) : expectedReturnType;
    wrapInCastIfNeeded(builder, nnReturnType, expression.getNominalType(), expression, context, new StatementWriter() {
      @Override
      public void writeStatement(StringBuilder builder, ExpressionContext context) {
        expression.accept(new ExpressionGenerator(builder, context));
      }
    });
    builder.append(';');
  }

  @Override
  public void visitApplicationStatement(GrApplicationStatement applicationStatement) {
    visitExpression(applicationStatement);
  }

  @Override
  public void visitTypeDefinition(GrTypeDefinition typeDefinition) {
    //todo ???????
  }

  @Override
  public void visitIfStatement(final GrIfStatement ifStatement) {
    GenerationUtil.writeStatement(builder, context, ifStatement, new StatementWriter() {
      @Override
      public void writeStatement(StringBuilder builder, ExpressionContext context) {
        final GrExpression condition = ifStatement.getCondition();
        final GrStatement thenBranch = ifStatement.getThenBranch();
        final GrStatement elseBranch = ifStatement.getElseBranch();
        builder.append("if (");
        if (condition != null) {
          final PsiType type = condition.getType();
          if (TypesUtil.unboxPrimitiveTypeWrapper(type) == PsiType.BOOLEAN) {
            condition.accept(new ExpressionGenerator(builder, context));
          }
          else {
            invokeMethodByName(
              condition,
              "asBoolean",
              GrExpression.EMPTY_ARRAY, GrNamedArgument.EMPTY_ARRAY, GrClosableBlock.EMPTY_ARRAY,
              new ExpressionGenerator(builder, context), ifStatement);
          }
        }
        builder.append(')');
        if (thenBranch != null) thenBranch.accept(new CodeBlockGenerator(builder, context.extend(), myExitPoints));
        if (ifStatement.getElseKeyword() != null) builder.append(" else ");
        if (elseBranch != null) elseBranch.accept(new CodeBlockGenerator(builder, context.extend(), myExitPoints));
      }
    });
  }

  @Override
  public void visitForStatement(GrForStatement forStatement) {
    //final StringBuilder builder = new StringBuilder();
    builder.append("for(");

    final GrForClause clause = forStatement.getClause();
    ExpressionContext forContext = context.extend();
    if (clause instanceof GrForInClause) {
      final GrExpression expression = ((GrForInClause)clause).getIteratedExpression();
      final GrVariable declaredVariable = clause.getDeclaredVariable();
      LOG.assertTrue(declaredVariable != null);

      writeVariableWithoutSemicolonAndInitializer(builder, declaredVariable, context);
      builder.append(" : ");
      if (expression != null) {
        final ExpressionContext context = forContext.copy();
        expression.accept(new ExpressionGenerator(builder, context));
      }
    }
    else if (clause instanceof GrTraditionalForClause) {
      final GrTraditionalForClause cl = (GrTraditionalForClause)clause;
      final GrCondition initialization = cl.getInitialization();
      final GrExpression condition = cl.getCondition();
      final GrExpression update = cl.getUpdate();

      if (initialization instanceof GrParameter) {
        StringBuilder partBuilder = new StringBuilder();
        writeVariableWithoutSemicolonAndInitializer(partBuilder, (GrParameter)initialization, context);
        final GrExpression initializer = ((GrParameter)initialization).getInitializerGroovy();
        if (initializer != null) {
          final ExpressionContext partContext = forContext.copy();
          partBuilder.append(" = ");
          initializer.accept(new ExpressionGenerator(partBuilder, partContext));
          for (String statement : partContext.myStatements) {
            builder.append(statement).append(", ");
          }
          builder.append(partBuilder);
        }
      }
      else {
        if (initialization != null) {
          StringBuilder partBuilder = new StringBuilder();
          final ExpressionContext partContext = forContext.copy();
          genForPart(builder, initialization, new CodeBlockGenerator(partBuilder, partContext, null));
        }
      }

      builder.append(';');
      if (condition != null) {
        genForPart(builder, condition, forContext.copy());                 //todo???
      }

      builder.append(';');
      if (update != null) {
        genForPart(builder, update, forContext.copy());
      }
    }
    builder.append(')');

    final GrStatement body = forStatement.getBody();
    if (body != null) {
      body.accept(new CodeBlockGenerator(builder, forContext, null));
    }
  }

  private static void genForPart(StringBuilder builder, GrExpression part, final ExpressionContext context) {
    genForPart(builder, part, new ExpressionGenerator(new StringBuilder(), context));
  }

  private static void genForPart(StringBuilder builder, GroovyPsiElement part, final Generator visitor) {
    part.accept(visitor);
    for (String statement : visitor.getContext().myStatements) {
      builder.append(statement).append(", ");
    }
    builder.append(visitor.getBuilder());
  }

  private static void writeVariableWithoutSemicolonAndInitializer(StringBuilder builder, GrVariable var, ExpressionContext context) {
    ModifierListGenerator.writeModifiers(builder, var.getModifierList());
    writeType(builder, context.typeProvider.getVarType(var), var);
    builder.append(' ').append(var.getName());
  }

  @Override
  public void visitWhileStatement(GrWhileStatement whileStatement) {
    final GrCondition condition = whileStatement.getCondition();
    final GrStatement body = whileStatement.getBody();

    StringBuilder builder = new StringBuilder();
    builder.append("while (");
    final ExpressionContext copy = context.copy();
    if (condition != null) {
      condition.accept(new ExpressionGenerator(builder, copy));           //todo update???
    }
    builder.append(" )");
    if (body != null) {
      body.accept(new CodeBlockGenerator(builder, copy.extend(), null));
    }
    writeStatement(builder, whileStatement, copy);
  }

  @Override
  public void visitSwitchStatement(GrSwitchStatement switchStatement) {
    final StringBuilder builder = new StringBuilder();
    final ExpressionContext copy = context.copy();
    SwitchStatementGenerator.generate(builder, copy, switchStatement);
    writeStatement(builder, switchStatement, copy);
  }

  @Override
  public void visitTryStatement(GrTryCatchStatement tryCatchStatement) {
    final GrOpenBlock tryBlock = tryCatchStatement.getTryBlock();
    final GrCatchClause[] catchClauses = tryCatchStatement.getCatchClauses();
    final GrFinallyClause finallyClause = tryCatchStatement.getFinallyClause();
    builder.append("try");
    tryBlock.accept(this);
    for (GrCatchClause catchClause : catchClauses) {
      catchClause.accept(this);
    }
    if (finallyClause != null) {
      finallyClause.accept(this);
    }
  }

  @Override
  public void visitCatchClause(GrCatchClause catchClause) {
    final GrParameter parameter = catchClause.getParameter();
    builder.append("catch (");
    writeVariableWithoutSemicolonAndInitializer(builder, parameter, context);
    builder.append(") ");
    final GrOpenBlock body = catchClause.getBody();
    if (body != null) {
      body.accept(this);
    }
  }

  @Override
  public void visitFinallyClause(GrFinallyClause finallyClause) {
    builder.append("finally ");
    final GrOpenBlock body = finallyClause.getBody();
    if (body != null) {
      body.accept(this);
    }
  }

  @Override
  public void visitBlockStatement(GrBlockStatement blockStatement) {
    blockStatement.getBlock().accept(this);
  }

  @Override
  public void visitSynchronizedStatement(GrSynchronizedStatement synchronizedStatement) {
    final GrExpression monitor = synchronizedStatement.getMonitor();
    final GrOpenBlock body = synchronizedStatement.getBody();

    StringBuilder statementBuilder = new StringBuilder();
    final ExpressionContext expressionContext = context.copy();

    statementBuilder.append("synchronized(");
    monitor.accept(new ExpressionGenerator(statementBuilder, expressionContext));
    statementBuilder.append(')');
    body.accept(new CodeBlockGenerator(statementBuilder, context.extend(), myExitPoints));
    writeStatement(statementBuilder, synchronizedStatement, expressionContext);
  }

  @Override
  public void visitVariableDeclaration(final GrVariableDeclaration variableDeclaration) {
    GenerationUtil.writeStatement(builder, context, variableDeclaration, new StatementWriter() {
      @Override
      public void writeStatement(StringBuilder builder, ExpressionContext context) {
        if (variableDeclaration.isTuple()) {
          writeTupleDeclaration(variableDeclaration, builder, context);
        }
        else {
          writeSimpleVarDeclaration(variableDeclaration, builder, context);
        }
      }
    });
  }

  private void writeTupleDeclaration(GrVariableDeclaration variableDeclaration,
                                     StringBuilder builder,
                                     ExpressionContext expressionContext) {
    GrVariable[] variables = variableDeclaration.getVariables();
    final GrExpression tupleInitializer = variableDeclaration.getTupleInitializer();
    if (tupleInitializer instanceof GrListOrMap) {
      for (GrVariable variable : variables) {
        writeVariableSeparately(variable, builder, expressionContext);
        builder.append(";\n");
      }
    }
    else if (tupleInitializer != null) {

      GroovyResolveResult iteratorMethodResult =
        resolveMethod(tupleInitializer, "iterator", GrExpression.EMPTY_ARRAY, GrNamedArgument.EMPTY_ARRAY, GrClosableBlock.EMPTY_ARRAY,
                      variableDeclaration);

      final PsiType iteratorType = inferIteratorType(iteratorMethodResult, tupleInitializer);

      final String iteratorName = genIteratorVar(variableDeclaration, builder, expressionContext, tupleInitializer, iteratorType,
                                                 iteratorMethodResult);

      final GrModifierList modifierList = variableDeclaration.getModifierList();

      PsiType iterableTypeParameter = PsiUtil.extractIterableTypeParameter(iteratorType, false);

      for (final GrVariable v : variables) {
        ModifierListGenerator.writeModifiers(builder, modifierList);
        final PsiType type = context.typeProvider.getVarType(v);
        writeType(builder, type, variableDeclaration);
        builder.append(' ').append(v.getName());

        builder.append(" = ");
        wrapInCastIfNeeded(builder, type, iterableTypeParameter, tupleInitializer, expressionContext, new StatementWriter() {
          @Override
          public void writeStatement(StringBuilder builder, ExpressionContext context) {
            builder.append(iteratorName).append(".hasNext() ? ").append(iteratorName).append(".next() : null");
          }
        });
        builder.append(";\n");
      }
    }
    else {
      writeSimpleVarDeclaration(variableDeclaration, builder, expressionContext);
    }
  }

  private static String genIteratorVar(GrVariableDeclaration variableDeclaration,
                                       StringBuilder builder,
                                       ExpressionContext expressionContext,
                                       @NotNull GrExpression tupleInitializer,
                                       PsiType iteratorType,
                                       GroovyResolveResult iteratorMethodResult) {

    final String iteratorName = suggestVarName(iteratorType, variableDeclaration, expressionContext);
    builder.append("final ");
    writeType(builder, iteratorType, variableDeclaration);
    builder.append(' ').append(iteratorName).append(" = ");

    invokeMethodByResolveResult(tupleInitializer, iteratorMethodResult, "iterator", GrExpression.EMPTY_ARRAY, GrNamedArgument.EMPTY_ARRAY,
                                GrClosableBlock.EMPTY_ARRAY, new ExpressionGenerator(builder, expressionContext), variableDeclaration);
    builder.append(";\n");
    return iteratorName;
  }

  private PsiType inferIteratorType(GroovyResolveResult iteratorMethodResult, GrExpression tupleInitializer) {
    PsiElement method = iteratorMethodResult.getElement();
    if (method instanceof PsiMethod) {
      return iteratorMethodResult.getSubstitutor().substitute(((PsiMethod)method).getReturnType());
    }
    else {
      PsiType initializerType = tupleInitializer.getType();
      PsiType iterableParam = PsiUtil.extractIterableTypeParameter(initializerType, false);

      JavaPsiFacade facade = JavaPsiFacade.getInstance(context.project);
      PsiClass iterableClass = facade.findClass(CommonClassNames.JAVA_UTIL_ITERATOR, tupleInitializer.getResolveScope());
      if (iterableClass != null && iterableParam != null) {
        return facade.getElementFactory().createType(iterableClass, iterableParam);
      }
      else {
        return facade.getElementFactory().createTypeFromText(CommonClassNames.JAVA_UTIL_ITERATOR, tupleInitializer);
      }
    }
  }

  @Override
  public void visitVariable(GrVariable variable) {
    super.visitVariable(variable);    //To change body of overridden methods use File | Settings | File Templates.
  }
}
