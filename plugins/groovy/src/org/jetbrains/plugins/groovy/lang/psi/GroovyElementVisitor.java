package org.jetbrains.plugins.groovy.lang.psi;

import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrThrowsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrParameterModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePairs;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForInClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrRegex;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrExtendsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrImplementsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrDefaultAnnotationMember;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstantList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.*;

/**
 * @author ven
 */
public abstract class GroovyElementVisitor {
  public void visitElement(GroovyPsiElement element) {
  }

  public void visitFile(GroovyFile file) {
  }

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

  public void visitEnumConstants(GrEnumConstantList enumConstantsSection) {
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

  public void visitApplicationStatement(GrApplicationStatement applicationStatement) {
    visitStatement(applicationStatement);
  }

  public void visitArrayDeclaration(GrArrayDeclaration arrayDeclaration) {
    visitElement(arrayDeclaration);
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

  public void visitReferenceExpression(GrReferenceExpression referenceExpression) {
    visitExpression(referenceExpression);
  }

  public void visitThisExpression(GrThisReferenceExpression thisExpression) {
    visitExpression(thisExpression);
  }

  public void visitSuperExpression(GrSuperReferenceExpression superExpression) {
    visitExpression(superExpression);
  }

  public void visitCastExpression(GrTypeCastExpression typeCastExpression) {
    visitExpression(typeCastExpression);
  }

  public void visitParenthesizedExpression(GrParenthesizedExpr expression) {
    visitExpression(expression);
  }

  public void visitPropertySelection(GrPropertySelection expression) {
    visitExpression(expression);
  }

  public void visitPropertySelector(GrPropertySelector selector) {
    visitElement(selector);
  }

  public void visitIndexProperty(GrIndexProperty expression) {
    visitExpression(expression);
  }

  public void visitLabel(GrLabel label) {
    visitElement(label);
  }

  public void visitArgumentList(GrArgumentList list) {
    visitElement(list);
  }

  public void visitNamedArgument(GrNamedArgument argument) {
    visitElement(argument);
  }

  public void visitArgumentLabel(GrArgumentLabel argumentLabel) {
    visitElement(argumentLabel);
  }

  public void visitListOrMap(GrListOrMap listOrMap) {
    visitExpression(listOrMap);
  }

  public void visitArrayTypeElement(GrArrayTypeElement typeElement) {
    visitElement(typeElement);
  }

  public void visitBuiltinTypeElement(GrBuiltInTypeElement typeElement) {
    visitElement(typeElement);
  }

  public void visitClassTypeElement(GrClassTypeElement typeElement) {
    visitElement(typeElement);
  }

  public void visitCodeReferenceElement(GrCodeReferenceElement refElement) {
    visitElement(refElement);
  }

  public void visitTypeDefinition(GrTypeDefinition typeDefinition) {
    visitElement(typeDefinition);
  }

  public void visitExtendsClause(GrExtendsClause extendsClause) {
    visitElement(extendsClause);
  }

  public void visitImplementsClause(GrImplementsClause implementsClause) {
    visitElement(implementsClause);
  }

  public void visitTypeArgumentList(GrTypeArgumentList typeArgumentList) {
    visitElement(typeArgumentList);
  }

  public void visitWildcardTypeArgument(GrWildcardTypeArgument wildcardTypeArgument) {
    visitElement(wildcardTypeArgument);
  }

  public void visitDefaultAnnotationMember(GrDefaultAnnotationMember defaultAnnotationMember) {
    visitElement(defaultAnnotationMember);
  }

  public void visitDefaultAnnotationValue(GrDefaultAnnotationValue defaultAnnotationValue) {
    visitElement(defaultAnnotationValue);
  }

  public void visitMethod(GrMethod method) {
    visitElement(method);
  }

  public void visitConstructorInvocation(GrConstructorInvocation invocation) {
    visitElement(invocation);
  }

  public void visitThrowsClause(GrThrowsClause throwsClause) {
    visitElement(throwsClause);
  }

  public void visitAnnotationArgumentList(GrAnnotationArgumentList annotationArgumentList) {
    visitElement(annotationArgumentList);
  }

  public void visitAnnotationNameValuePair(GrAnnotationNameValuePair nameValuePair) {
    visitElement(nameValuePair);
  }

  public void visitAnnotationNameValuePairs(GrAnnotationNameValuePairs nameValuePair) {
    visitElement(nameValuePair);
  }

  public void visitAnnotation(GrAnnotation annotation) {
    visitElement(annotation);
  }

  public void visitParameterModifierList(GrParameterModifierList parameterModifierList) {
    visitElement(parameterModifierList);
  }

  public void visitParameterList(GrParameterList parameterList) {
    visitElement(parameterList);
  }

  public void visitParameter(GrParameter parameter) {
    visitVariable(parameter);
  }

  public void visitField(GrField field) {
    visitVariable(field);
  }

  public void visitTypeDefinitionBody(GrTypeDefinitionBody typeDefinitionBody) {
    visitElement(typeDefinitionBody);
  }

  public void visitIfStatement(GrIfStatement ifStatement) {
    visitStatement(ifStatement);
  }

  public void visitForStatement(GrForStatement forStatement) {
    visitStatement(forStatement);
  }

  public void visitWhileStatement(GrWhileStatement whileStatement) {
    visitStatement(whileStatement);
  }

  public void visitWithStatement(GrWithStatement withStatement) {
    visitStatement(withStatement);
  }

  public void visitSwitchStatement(GrSwitchStatement switchStatement) {
    visitStatement(switchStatement);
  }

  public void visitCaseBlock(GrCaseBlock caseBlock) {
    visitElement(caseBlock);
  }

  public void visitCaseLabel(GrCaseLabel caseLabel) {
    visitElement(caseLabel);
  }

  public void visitForInClause(GrForInClause forInClause) {
    visitForClause(forInClause);
  }

  public void visitForClause(GrForClause forClause) {
    visitElement(forClause);
  }

  public void visitTryStatement(GrTryCatchStatement tryCatchStatement) {
    visitStatement(tryCatchStatement);
  }

  public void visitCatchClause(GrCatchClause catchClause) {
    visitElement(catchClause);
  }

  public void visitFinallyClause(GrFinallyClause catchClause) {
    visitElement(catchClause);
  }

  public void visitSynchronizedStatement(GrSynchronizedStatement synchronizedStatement) {
    visitStatement(synchronizedStatement);
  }

  public void visitVariableDeclaration(GrVariableDeclaration variableDeclaration) {
    visitStatement(variableDeclaration);
  }

  public void visitVariable(GrVariable variable) {
    visitElement(variable);
  }

  public void visitModifierList(GrModifierList modifierList) {
    visitElement(modifierList);
  }
}
