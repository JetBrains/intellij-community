/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrCondition;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.formatter.GrControlStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrAssertStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrFlowInterruptingStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrThrowStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

/**
 * @author Maxim.Medvedev
 */
public class CodeBlockGenerator extends GroovyRecursiveElementVisitor {

  private final StringBuilder builder;
  private final Project project;
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.refactoring.convertToJava.CodeBlockGenerator");

  public CodeBlockGenerator(StringBuilder builder, Project project) {
    this.builder = builder;
    this.project = project;
  }

  @Override
  public void visitOpenBlock(GrOpenBlock block) {
    builder.append("{\n");
    super.visitOpenBlock(block);
    builder.append("}\n");
  }

  @Override
  public void visitStatement(GrStatement statement) {
    super.visitStatement(statement);    //To change body of overridden methods use File | Settings | File Templates.
  }

  @Override
  public void visitFlowInterruptStatement(GrFlowInterruptingStatement statement) {
    builder.append(statement.getStatementText());
    final String name = statement.getLabelName();
    if (name != null) {
      builder.append(" ").append(name); //todo check incorrect labels
    }
    builder.append(";");
  }

  @Override
  public void visitReturnStatement(GrReturnStatement returnStatement) {
    final GrExpression returnValue = returnStatement.getReturnValue();
    if (returnValue == null) {
      builder.append("return;\n");
      return;
    }

    final ExpressionGenerator expressionGenerator = new ExpressionGenerator(project);
    returnValue.accept(expressionGenerator);
    StringBuilder builder = new StringBuilder();
    builder.append("return").append(expressionGenerator.getBuilder()).append(";"); //todo add casts to return type
    writeStatement(builder, returnStatement, expressionGenerator.getContext());
  }

  private void writeStatement(StringBuilder statementBuilder, GrStatement statement) {
    writeStatement(statementBuilder, statement, null);
  }

  private void writeStatement(StringBuilder statementBuilder, GrStatement statement, @Nullable ExpressionContext context) {
    final PsiElement parent = statement.getParent();

    final boolean addParentheses = context != null && context.myStatements.size() > 0 && parent instanceof GrControlStatement;
    if (addParentheses) {
      builder.append("{\n");
    }

    if (context != null) {
      for (String st : context.myStatements) {
        builder.append(st).append("\n");
      }
    }

    builder.append(statementBuilder);
    if (addParentheses) {
      builder.append("}\n");
    }
  }

  @Override
  public void visitAssertStatement(GrAssertStatement assertStatement) {
    final GrExpression assertion = assertStatement.getAssertion();
    final ExpressionGenerator expressionGenerator = new ExpressionGenerator(project);
    assertion.accept(expressionGenerator);
    final StringBuilder builder = new StringBuilder("assert ").append(expressionGenerator.getBuilder()).append(";");
    writeStatement(builder, assertStatement, expressionGenerator.getContext());
  }

  @Override
  public void visitThrowStatement(GrThrowStatement throwStatement) {
    final GrExpression exception = throwStatement.getException();
    final ExpressionGenerator expressionGenerator = new ExpressionGenerator(project);
    exception.accept(expressionGenerator);
    final StringBuilder builder =
      new StringBuilder("throw ").append(expressionGenerator.getBuilder()).append(";"); //todo add exception to method 'throws' list
    writeStatement(builder, throwStatement, expressionGenerator.getContext());
  }

  @Override
  public void visitLabeledStatement(GrLabeledStatement labeledStatement) {
    final String label = labeledStatement.getLabelName();
    final GrStatement statement = labeledStatement.getStatement();

    StringBuilder statementBuilder = new StringBuilder();
    statementBuilder.append(label).append(": ");
    if (statement != null) {
      statement.accept(new CodeBlockGenerator(statementBuilder, project));
    }
    writeStatement(statementBuilder, labeledStatement);
  }

  @Override
  public void visitExpression(GrExpression expression) {
    final StringBuilder statementBuilder = new StringBuilder();
    final ExpressionContext context = new ExpressionContext(project);
    expression.accept(new ExpressionGenerator(statementBuilder, context));
    writeStatement(statementBuilder, expression, context);
  }

  @Override
  public void visitTypeDefinition(GrTypeDefinition typeDefinition) {
    //todo ???????
  }

  @Override
  public void visitIfStatement(GrIfStatement ifStatement) {
    final GrCondition condition = ifStatement.getCondition();
    final GrStatement thenBranch = ifStatement.getThenBranch();
    final GrStatement elseBranch = ifStatement.getElseBranch();

    StringBuilder statementBuilder = new StringBuilder();
    final CodeBlockGenerator innerGenerator = new CodeBlockGenerator(statementBuilder, project);
    final ExpressionContext expressionContext = new ExpressionContext(project);

    statementBuilder.append("if (");
    if (condition != null) condition.accept(new ExpressionGenerator(statementBuilder, expressionContext));
    statementBuilder.append(")");
    if (thenBranch != null) thenBranch.accept(innerGenerator);
    if (ifStatement.getElseKeyword() != null) statementBuilder.append("else");
    if (elseBranch != null) elseBranch.accept(innerGenerator);

    writeStatement(statementBuilder, ifStatement, expressionContext);
  }

  @Override
  public void visitForStatement(GrForStatement forStatement) {
    final GrForClause clause = forStatement.getClause();
    if (clause instanceof GrForInClause) {
      final GrExpression expression = ((GrForInClause)clause).getIteratedExpression();
      final GrVariable[] declaredVariables = clause.getDeclaredVariables();
      LOG.assertTrue(declaredVariables.length == 1);

      final StringBuilder builder = new StringBuilder();
      builder.append("for(");

    }
    else {

    }
  }

  @Override
  public void visitWhileStatement(GrWhileStatement whileStatement) {
    //todo
  }

  @Override
  public void visitSwitchStatement(GrSwitchStatement switchStatement) {
    super.visitSwitchStatement(switchStatement);    //To change body of overridden methods use File | Settings | File Templates.
  }

  @Override
  public void visitCaseSection(GrCaseSection caseSection) {
    super.visitCaseSection(caseSection);    //To change body of overridden methods use File | Settings | File Templates.
  }

  @Override
  public void visitCaseLabel(GrCaseLabel caseLabel) {
    super.visitCaseLabel(caseLabel);    //To change body of overridden methods use File | Settings | File Templates.
  }

  @Override
  public void visitForInClause(GrForInClause forInClause) {
    super.visitForInClause(forInClause);    //To change body of overridden methods use File | Settings | File Templates.
  }

  @Override
  public void visitForClause(GrForClause forClause) {
    super.visitForClause(forClause);    //To change body of overridden methods use File | Settings | File Templates.
  }

  @Override
  public void visitTraditionalForClause(GrTraditionalForClause forClause) {
    super.visitTraditionalForClause(forClause);    //To change body of overridden methods use File | Settings | File Templates.
  }

  @Override
  public void visitTryStatement(GrTryCatchStatement tryCatchStatement) {
    super.visitTryStatement(tryCatchStatement);    //To change body of overridden methods use File | Settings | File Templates.
  }

  @Override
  public void visitBlockStatement(GrBlockStatement blockStatement) {
    super.visitBlockStatement(blockStatement);    //To change body of overridden methods use File | Settings | File Templates.
  }

  @Override
  public void visitCatchClause(GrCatchClause catchClause) {
    super.visitCatchClause(catchClause);    //To change body of overridden methods use File | Settings | File Templates.
  }

  @Override
  public void visitFinallyClause(GrFinallyClause catchClause) {
    super.visitFinallyClause(catchClause);    //To change body of overridden methods use File | Settings | File Templates.
  }

  @Override
  public void visitSynchronizedStatement(GrSynchronizedStatement synchronizedStatement) {
    final GrExpression monitor = synchronizedStatement.getMonitor();
    final GrOpenBlock body = synchronizedStatement.getBody();

    StringBuilder statementBuilder = new StringBuilder();
    final ExpressionContext expressionContext = new ExpressionContext(project);

    statementBuilder.append("synchronized(");
    monitor.accept(new ExpressionGenerator(statementBuilder, expressionContext));
    statementBuilder.append(")");
    body.accept(new CodeBlockGenerator(statementBuilder, project));
    writeStatement(statementBuilder, synchronizedStatement, expressionContext);
  }

  @Override
  public void visitVariableDeclaration(GrVariableDeclaration variableDeclaration) {
    final GrModifierList modifierList = variableDeclaration.getModifierList();
    final GrVariable[] variables = variableDeclaration.getVariables();

    StringBuilder builder=new StringBuilder();
    ExpressionContext expressionContext=new ExpressionContext(project);
    final ExpressionGenerator expressionGenerator = new ExpressionGenerator(builder, expressionContext);

    if (variableDeclaration.isTuple()) {
      final GrTupleDeclaration tuple = variableDeclaration.getTupleDeclaration();
      final GrExpression tupleInitializer = tuple.getInitializerGroovy();
      if (tupleInitializer instanceof GrListOrMap) {
        final GrExpression[] initializers = ((GrListOrMap)tupleInitializer).getInitializers();
        for (int i = 0; i < variables.length; i++) {
          final GrVariable v = variables[i];
          final PsiType type = v.getDeclaredType();
          GenerationUtil.writeType(builder, type);
          builder.append(" ").append(v.getName());
          if (i < initializers.length) {
            builder.append(" = ");
            initializers[i].accept(expressionGenerator);
          }
          builder.append(";\n");
        }
      }
      else {
        final PsiType iteratorType =
          JavaPsiFacade.getElementFactory(project).createTypeFromText(CommonClassNames.JAVA_UTIL_ITERATOR, variableDeclaration);
        final String iteratorName = GenerationUtil.suggestVarName(iteratorType,variableDeclaration, expressionContext);
        builder.append("final ").append(CommonClassNames.JAVA_UTIL_ITERATOR).append(" ").append(iteratorName).append(" = ");

        GenerationUtil.invokeMethodByName(tupleInitializer, "iterator", GrExpression.EMPTY_ARRAY, GrNamedArgument.EMPTY_ARRAY,
                                          GrClosableBlock.EMPTY_ARRAY, expressionGenerator, variableDeclaration);
        for (final GrVariable v : variables) {
          final PsiType type = v.getDeclaredType();
          GenerationUtil.writeType(builder, type);
          builder.append(" ").append(v.getName());
          builder.append(" = ").append(iteratorName).append(".hasNext() ? ").append(iteratorName).append(".next() : null;");
        }
      }
    }
    else {
      final GrTypeElement typeElement = variableDeclaration.getTypeElementGroovy();
      PsiType type = typeElement == null
                     ? PsiType.getJavaLangObject(variableDeclaration.getManager(), variableDeclaration.getResolveScope())
                     : typeElement.getType();
      GenerationUtil.writeType(builder, type);


    }
    writeStatement(builder, variableDeclaration, expressionContext);
  }

  @Override
  public void visitVariable(GrVariable variable) {
    super.visitVariable(variable);    //To change body of overridden methods use File | Settings | File Templates.
  }
}
