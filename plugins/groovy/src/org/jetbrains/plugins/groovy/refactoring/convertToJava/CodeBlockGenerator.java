// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.convertToJava;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.noReturnMethod.MissingReturnInspection;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GrExpressionList;
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
import org.jetbrains.plugins.groovy.transformations.impl.GroovyObjectTransformationSupport;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Maxim.Medvedev
 */
public class CodeBlockGenerator extends Generator {
  private static final Logger LOG = Logger.getInstance(CodeBlockGenerator.class);

  private static final boolean IN_TEST = ApplicationManager.getApplication().isUnitTestMode();

  private final StringBuilder builder;
  private final ExpressionContext context;

  private final Set<GrStatement> myExitPoints;

  public CodeBlockGenerator(StringBuilder builder, ExpressionContext context) {
    this(builder, context, null);
  }

  public CodeBlockGenerator(StringBuilder builder, ExpressionContext context, @Nullable Collection<GrStatement> exitPoints) {
    this.builder = builder;
    this.context = context;
    myExitPoints = new HashSet<>();
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

    boolean shouldInsertReturnNull;
    myExitPoints.clear();
    PsiType returnType = context.typeProvider.getReturnType(method);
    if (GroovyObjectTransformationSupport.isGroovyObjectSupportMethod(method)) {
      shouldInsertReturnNull = !(returnType instanceof PsiPrimitiveType);
    }
    else if (!method.isConstructor() && !PsiTypes.voidType().equals(returnType)) {
      myExitPoints.addAll(ControlFlowUtils.collectReturns(block));
      shouldInsertReturnNull = block != null &&
                               !(returnType instanceof PsiPrimitiveType) &&
                               MissingReturnInspection.methodMissesSomeReturns(block, MissingReturnInspection.ReturnStatus.getReturnStatus(
                                 method));
    }
    else {
      shouldInsertReturnNull = false;
    }

    generateCodeBlock(method.getParameters(), block, shouldInsertReturnNull);
  }

  @Override
  public void visitMethod(@NotNull GrMethod method) {
    LOG.error("don't invoke it!!!");
  }

  @Override
  public void visitOpenBlock(@NotNull GrOpenBlock block) {
    GrParameter[] parameters;
    if (block.getParent() instanceof GrMethod method) {
      parameters = method.getParameters();
    }
    else {
      parameters = GrParameter.EMPTY_ARRAY;
    }
    generateCodeBlock(parameters, block, false);
  }

  public void generateCodeBlock(GrParameter @NotNull [] parameters, @Nullable GrCodeBlock block, boolean shouldInsertReturnNull) {
    builder.append("{");

    for (GrParameter parameter : parameters) {
      if (context.analyzedVars.toWrap(parameter)) {
        StringBuilder typeText = new StringBuilder().append(GroovyCommonClassNames.GROOVY_LANG_REFERENCE);
        GenerationUtil.writeTypeParameters(typeText, new PsiType[]{context.typeProvider.getParameterType(parameter)}, parameter,
                            new GeneratorClassNameProvider());
        builder.append("final ").append(typeText).append(' ').append(context.analyzedVars.toVarName(parameter))
          .append(" = new ").append(typeText).append('(').append(parameter.getName()).append(");\n");
      }
    }
    visitStatementOwner(block, shouldInsertReturnNull);
    builder.append("}\n");
  }

  public void visitStatementOwner(@Nullable GrStatementOwner owner, boolean shouldInsertReturnNull) {
    boolean hasLineFeed = false;
    for (PsiElement e = owner == null ? null : owner.getFirstChild(); e != null; e = e.getNextSibling()) {
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


  private void writeStatement(@Nullable GrStatement statement, @NotNull StatementWriter writer) {
    GenerationUtil.writeStatement(builder, context, statement, writer);
  }

  private static void writeExpression(@NotNull GrExpression expression, @NotNull StringBuilder builder, @NotNull ExpressionContext context) {
    expression.accept(new ExpressionGenerator(builder, context));
  }

  @Override
  public void visitConstructorInvocation(@NotNull final GrConstructorInvocation invocation) {
    writeStatement(invocation, new StatementWriter() {
      @Override
      public void writeStatement(StringBuilder builder, ExpressionContext context) {
        final GrReferenceExpression thisOrSuperKeyword = invocation.getInvokedExpression();
        final GrArgumentList argumentList = invocation.getArgumentList();
        final GroovyResolveResult resolveResult = invocation.advancedResolve();
        if (thisOrSuperKeyword.getQualifier() == null) {
          builder.append(thisOrSuperKeyword.getReferenceName());
        }
        else {
          writeExpression(thisOrSuperKeyword, builder, context);
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

  @Override
  public void visitStatement(@NotNull GrStatement statement) {
    LOG.error("all statements must be overloaded");
  }

  @Override
  public void visitFlowInterruptStatement(@NotNull GrFlowInterruptingStatement statement) {
    builder.append(statement.getStatementText());
    final String name = statement.getLabelName();
    if (name != null) {
      builder.append(' ').append(name); //todo check incorrect labels
    }
    builder.append(';');
  }

  @Override
  public void visitReturnStatement(@NotNull final GrReturnStatement returnStatement) {
    final GrExpression returnValue = returnStatement.getReturnValue();
    if (returnValue == null) {
      builder.append("return;\n");
      return;
    }

    writeStatement(returnStatement, new StatementWriter() {
      @Override
      public void writeStatement(StringBuilder builder, ExpressionContext context) {
        writeReturn(builder, context, returnValue);
      }
    });
  }

  @Override
  public void visitAssertStatement(@NotNull final GrAssertStatement assertStatement) {
    final GrExpression assertion = assertStatement.getAssertion();
    final GrExpression message = assertStatement.getErrorMessage();
    if (assertion != null) {
      writeStatement(assertStatement, new StatementWriter() {
        @Override
        public void writeStatement(StringBuilder builder, ExpressionContext context) {
          builder.append("assert ");
          writeExpression(assertion, builder, context);
          if (message != null) {
            builder.append(" : ");
            writeExpression(message, builder, context);
          }
          builder.append(';');
        }
      });
    }
    else if (message != null) {
      writeStatement(assertStatement, new StatementWriter() {
        @Override
        public void writeStatement(StringBuilder builder, ExpressionContext context) {
          builder.append("assert : ");
          writeExpression(message, builder, context);
          builder.append(';');
        }
      });
    }
    else {
      builder.append("assert;");
    }
  }

  @Override
  public void visitThrowStatement(@NotNull GrThrowStatement throwStatement) {
    final GrExpression exception = throwStatement.getException();
    if (exception == null) {
      builder.append("throw ;");
      return;
    }
    writeStatement(throwStatement, new StatementWriter() {
      @Override
      public void writeStatement(StringBuilder builder, ExpressionContext context) {
        builder.append("throw ");
        writeExpression(exception, builder, context);                     //todo add exception to method 'throws' list
        builder.append(';');
      }
    });
  }

  @Override
  public void visitLabeledStatement(@NotNull final GrLabeledStatement labeledStatement) {
    writeStatement(labeledStatement, new StatementWriter() {
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
  public void visitExpression(@NotNull final GrExpression expression) {
    writeStatement(expression, new StatementWriter() {
      @Override
      public void writeStatement(StringBuilder builder, ExpressionContext context) {
        if (myExitPoints.contains(expression) && isRealExpression(expression)) {
          writeReturn(builder, context, expression);
        }
        else {
          writeExpression(expression, builder, context);
          builder.append(';');
        }
      }

      private boolean isRealExpression(GrExpression expression) {
        final PsiType type = expression.getType();

        if (PsiTypes.voidType().equals(type)) return false; //statement

        if (type == PsiTypes.nullType()) return !org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.isVoidMethodCall(expression);

        return true;
      }
    });
  }

  private static void writeReturn(StringBuilder builder, ExpressionContext context, final GrExpression expression) {
    builder.append("return ");

    final PsiType expectedReturnType = PsiImplUtil.inferReturnType(expression);
    final PsiType nnReturnType = expectedReturnType == null || PsiTypes.voidType().equals(expectedReturnType)
                                 ? TypesUtil.getJavaLangObject(expression) : expectedReturnType;
    GenerationUtil.wrapInCastIfNeeded(builder, nnReturnType, expression.getNominalType(), expression, context, new StatementWriter() {
      @Override
      public void writeStatement(StringBuilder builder, ExpressionContext context) {
        writeExpression(expression, builder, context);
      }
    });
    builder.append(';');
  }

  @Override
  public void visitApplicationStatement(@NotNull GrApplicationStatement applicationStatement) {
    visitExpression(applicationStatement);
  }

  @Override
  public void visitTypeDefinition(@NotNull GrTypeDefinition typeDefinition) {
    //todo ???????
  }

  @Override
  public void visitIfStatement(@NotNull final GrIfStatement ifStatement) {
    writeStatement(ifStatement, new StatementWriter() {
      @Override
      public void writeStatement(StringBuilder builder, ExpressionContext context) {
        final GrExpression condition = ifStatement.getCondition();
        final GrStatement thenBranch = ifStatement.getThenBranch();
        final GrStatement elseBranch = ifStatement.getElseBranch();
        builder.append("if (");
        if (condition != null) {
          final PsiType type = condition.getType();
          if (PsiTypes.booleanType().equals(TypesUtil.unboxPrimitiveTypeWrapper(type))) {
            writeExpression(condition, builder, context);
          }
          else {
            GenerationUtil.invokeMethodByName(
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
  public void visitForStatement(@NotNull GrForStatement forStatement) {
    builder.append("for(");

    final GrForClause clause = forStatement.getClause();
    ExpressionContext forContext = context.extend();
    if (clause instanceof GrForInClause) {
      final GrExpression expression = ((GrForInClause)clause).getIteratedExpression();
      final GrVariable declaredVariable = ((GrForInClause)clause).getDeclaredVariable();
      LOG.assertTrue(declaredVariable != null);

      writeVariableWithoutSemicolonAndInitializer(builder, declaredVariable, context);
      builder.append(" : ");
      if (expression != null) {
        final ExpressionContext context = forContext.copy();
        writeExpression(expression, builder, context);
      }
    }
    else if (clause instanceof GrTraditionalForClause cl) {
      final GrCondition initialization = cl.getInitialization();
      final GrExpression condition = cl.getCondition();
      final GrExpressionList update = cl.getUpdate();

      if (initialization instanceof GrParameter) {
        StringBuilder partBuilder = new StringBuilder();
        writeVariableWithoutSemicolonAndInitializer(partBuilder, (GrParameter)initialization, context);
        final GrExpression initializer = ((GrParameter)initialization).getInitializerGroovy();
        if (initializer != null) {
          final ExpressionContext partContext = forContext.copy();
          partBuilder.append(" = ");
          writeExpression(initializer, partBuilder, partContext);
          for (String statement : partContext.myStatements) {
            builder.append(statement).append(", ");
          }
          builder.append(partBuilder);
        }
      }
      else if (initialization != null) {
        genForPart(builder, initialization, new CodeBlockGenerator(new StringBuilder(), forContext.copy(), null));
      }

      if (condition != null) {
        genForPart(builder, condition, new ExpressionGenerator(new StringBuilder(), forContext.copy()));
      }

      builder.append(';');
      if (update != null) {
        genForPart(builder, update, new ExpressionGenerator(new StringBuilder(), forContext.copy()));
      }
    }
    builder.append(')');

    final GrStatement body = forStatement.getBody();
    if (body != null) {
      body.accept(new CodeBlockGenerator(builder, forContext, null));
    }
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
    TypeWriter.writeType(builder, context.typeProvider.getVarType(var), var);
    builder.append(' ').append(var.getName());
  }

  @Override
  public void visitWhileStatement(@NotNull final GrWhileStatement whileStatement) {
    writeStatement(whileStatement, new StatementWriter() {
      @Override
      public void writeStatement(StringBuilder builder, ExpressionContext context) {
        final GrExpression condition = whileStatement.getCondition();
        final GrStatement body = whileStatement.getBody();

        builder.append("while (");
        if (condition != null) {
          writeExpression(condition, builder, context);
        }
        builder.append(" )");
        if (body != null) {
          body.accept(new CodeBlockGenerator(builder, context.extend(), null));
        }
      }
    });
  }

  @Override
  public void visitSwitchStatement(@NotNull final GrSwitchStatement switchStatement) {
    writeStatement(switchStatement, new StatementWriter() {
      @Override
      public void writeStatement(StringBuilder builder, ExpressionContext context) {
        SwitchStatementGenerator.generate(builder, context, switchStatement);
      }
    });
  }

  @Override
  public void visitTryStatement(@NotNull GrTryCatchStatement tryCatchStatement) {
    builder.append("try");
    final GrOpenBlock tryBlock = tryCatchStatement.getTryBlock();
    if (tryBlock == null) {
      builder.append("{}");
    }
    else {
      tryBlock.accept(this);
    }
    final GrCatchClause[] catchClauses = tryCatchStatement.getCatchClauses();
    final GrFinallyClause finallyClause = tryCatchStatement.getFinallyClause();
    for (GrCatchClause catchClause : catchClauses) {
      catchClause.accept(this);
    }
    if (finallyClause != null) {
      finallyClause.accept(this);
    }
  }

  @Override
  public void visitCatchClause(@NotNull GrCatchClause catchClause) {
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
  public void visitFinallyClause(@NotNull GrFinallyClause finallyClause) {
    builder.append("finally ");
    final GrOpenBlock body = finallyClause.getBody();
    if (body != null) {
      body.accept(this);
    }
  }

  @Override
  public void visitBlockStatement(@NotNull GrBlockStatement blockStatement) {
    blockStatement.getBlock().accept(this);
  }

  @Override
  public void visitSynchronizedStatement(@NotNull final GrSynchronizedStatement statement) {
    writeStatement(statement, new StatementWriter() {
      @Override
      public void writeStatement(StringBuilder builder, ExpressionContext context) {
        final GrExpression monitor = statement.getMonitor();
        final GrOpenBlock body = statement.getBody();

        builder.append("synchronized(");
        if (monitor != null ) {
          writeExpression(monitor, builder, context);
        }
        builder.append(')');
        if (body != null) {
          body.accept(new CodeBlockGenerator(builder, context.extend(), myExitPoints));
        }
      }
    });
  }

  @Override
  public void visitVariableDeclaration(@NotNull final GrVariableDeclaration variableDeclaration) {
    writeStatement(variableDeclaration, new StatementWriter() {
      @Override
      public void writeStatement(StringBuilder builder, ExpressionContext context) {
        if (variableDeclaration.isTuple()) {
          writeTupleDeclaration(variableDeclaration, builder, context);
        }
        else {
          GenerationUtil.writeSimpleVarDeclaration(variableDeclaration, builder, context);
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
        GenerationUtil.writeVariableSeparately(variable, builder, expressionContext);
        builder.append(";\n");
      }
    }
    else if (tupleInitializer != null) {

      GroovyResolveResult iteratorMethodResult =
        GenerationUtil
          .resolveMethod(tupleInitializer, "iterator", GrExpression.EMPTY_ARRAY, GrNamedArgument.EMPTY_ARRAY, GrClosableBlock.EMPTY_ARRAY,
                      variableDeclaration);

      final PsiType iteratorType = inferIteratorType(iteratorMethodResult, tupleInitializer);

      final String iteratorName = genIteratorVar(variableDeclaration, builder, expressionContext, tupleInitializer, iteratorType,
                                                 iteratorMethodResult);

      final GrModifierList modifierList = variableDeclaration.getModifierList();

      PsiType iterableTypeParameter = PsiUtil.extractIterableTypeParameter(iteratorType, false);

      for (final GrVariable v : variables) {
        ModifierListGenerator.writeModifiers(builder, modifierList);
        final PsiType type = context.typeProvider.getVarType(v);
        TypeWriter.writeType(builder, type, variableDeclaration);
        builder.append(' ').append(v.getName());

        builder.append(" = ");
        GenerationUtil.wrapInCastIfNeeded(builder, type, iterableTypeParameter, tupleInitializer, expressionContext, new StatementWriter() {
          @Override
          public void writeStatement(StringBuilder builder, ExpressionContext context) {
            builder.append(iteratorName).append(".hasNext() ? ").append(iteratorName).append(".next() : null");
          }
        });
        builder.append(";\n");
      }
    }
    else {
      GenerationUtil.writeSimpleVarDeclaration(variableDeclaration, builder, expressionContext);
    }
  }

  private static String genIteratorVar(GrVariableDeclaration variableDeclaration,
                                       StringBuilder builder,
                                       ExpressionContext expressionContext,
                                       @NotNull GrExpression tupleInitializer,
                                       PsiType iteratorType,
                                       GroovyResolveResult iteratorMethodResult) {

    final String iteratorName = GenerationUtil.suggestVarName(iteratorType, variableDeclaration, expressionContext);
    builder.append("final ");
    TypeWriter.writeType(builder, iteratorType, variableDeclaration);
    builder.append(' ').append(iteratorName).append(" = ");

    GenerationUtil
      .invokeMethodByResolveResult(tupleInitializer, iteratorMethodResult, "iterator", GrExpression.EMPTY_ARRAY, GrNamedArgument.EMPTY_ARRAY,
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
}
