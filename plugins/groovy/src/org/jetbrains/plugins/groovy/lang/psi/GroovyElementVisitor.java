package org.jetbrains.plugins.groovy.lang.psi;

import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrLabeledStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrRegex;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstants;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;

/**
 * @author ven
 */
public abstract class GroovyElementVisitor {
  public void visitElement(GroovyPsiElement element) {}

  public void visitFile(GroovyFile file) {}

  public void visitPackageDefinition(GrPackageDefinition packageDefinition) {
    visitElement(packageDefinition);
  }

  public void visitStatement(GrStatement statement) {
    visitElement(statement);
  }

  public void visitClosure(GrClosableBlock closure) {
    visitStatement(closure);
  }

  public void visitBlock(GrOpenBlock block) {
    visitElement(block);
  }

  public void visitEnumConstants(GrEnumConstants enumConstantsSection) {
    visitElement(enumConstantsSection);
  }

  public void visitEnumConstant(GrEnumConstant enumConstant) {
    visitElement(enumConstant);
  }

  public void visitImportStatement(GrImportStatement importStatement) {
    visitElement(importStatement);
  }

  public void visitBreakStatement(GrBreakStatement breakStatement) {
    visitStatement(breakStatement);
  }

  public void visitContinueStatement(GrContinueStatement continueStatement) {
    visitStatement(continueStatement);
  }

  public void visitReturnStatement(GrReturnStatement returnStatement) {
    visitStatement(returnStatement);
  }

  public void visitAssertStatement(GrAssertStatement assertStatement) {
    visitStatement(assertStatement);
  }

  public void visitThrowStatement(GrThrowStatement throwStatement) {
    visitStatement(throwStatement);
  }

  public void visitLabeledStatement(GrLabeledStatement labeledStatement) {
    visitStatement(labeledStatement);
  }

  public void visitExpression(GrExpression expression) {
    visitElement(expression);
  }

  public void visitCallExpression(GrCallExpression callExpression) {
    visitExpression(callExpression);
  }

  public void visitMethodCallExpression(GrMethodCallExpression methodCallExpression) {
    visitCallExpression(methodCallExpression);
  }

  public void visitNewExpression(GrNewExpression newExpression) {
    visitCallExpression(newExpression);
  }

  public void visitCommandArguments(GrCommandArgumentList argumentList) {
    visitElement(argumentList);
  }

  public void visitCommandArgument(GrCommandArgument argument) {
    visitElement(argument);
  }

  public void visitConditionalExpression(GrConditionalExpression expression) {
    visitExpression(expression);
  }

  public void visitAssignmentExpression(GrAssignmentExpression expression) {
    visitExpression(expression);
  }

  public void visitBinaryExpression(GrBinaryExpression expression) {
    visitExpression(expression);
  }

  public void visitUnaryExpression(GrUnaryExpression expression) {
    visitExpression(expression);
  }

  public void visitPostfixExpression(GrPostfixExpression expression) {
    visitUnaryExpression(expression);
  }

  public void visitRegexExpression(GrRegex expression) {
    visitLiteralExpression(expression);
  }

  public void visitLiteralExpression(GrLiteral literal) {
    visitExpression(literal);
  }

  public void visitGStringExpression(GrString gstring) {
    visitExpression(gstring);
  }
}
