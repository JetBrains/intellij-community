// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.*;
import org.jetbrains.plugins.groovy.lang.psi.api.GrInExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrThrowsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationArrayInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrSpreadArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.arithmetic.GrRangeExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrRegex;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrPropertySelection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAnnotationMethod;
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
  public void visitElement(@NotNull GroovyPsiElement element) {
  }

  public void visitFile(@NotNull GroovyFileBase file) {
    visitElement(file);
  }

  public void visitPackageDefinition(@NotNull GrPackageDefinition packageDefinition) {
    visitElement(packageDefinition);
  }

  public void visitStatement(@NotNull GrStatement statement) {
    visitElement(statement);
  }

  public void visitClosure(@NotNull GrClosableBlock closure) {
    visitStatement(closure);
  }

  public void visitOpenBlock(@NotNull GrOpenBlock block) {
    visitElement(block);
  }

  public void visitEnumConstants(@NotNull GrEnumConstantList enumConstantsSection) {
    visitElement(enumConstantsSection);
  }

  public void visitEnumConstant(@NotNull GrEnumConstant enumConstant) {
    visitField(enumConstant);
  }

  public void visitImportStatement(@NotNull GrImportStatement importStatement) {
    visitElement(importStatement);
  }

  public void visitBreakStatement(@NotNull GrBreakStatement breakStatement) {
    visitFlowInterruptStatement(breakStatement);
  }

  public void visitContinueStatement(@NotNull GrContinueStatement continueStatement) {
    visitFlowInterruptStatement(continueStatement);
  }

  public void visitFlowInterruptStatement(@NotNull GrFlowInterruptingStatement statement) {
    visitStatement(statement);
  }

  public void visitReturnStatement(@NotNull GrReturnStatement returnStatement) {
    visitStatement(returnStatement);
  }

  public void visitAssertStatement(@NotNull GrAssertStatement assertStatement) {
    visitStatement(assertStatement);
  }

  public void visitThrowStatement(@NotNull GrThrowStatement throwStatement) {
    visitStatement(throwStatement);
  }

  public void visitLabeledStatement(@NotNull GrLabeledStatement labeledStatement) {
    visitStatement(labeledStatement);
  }

  public void visitExpression(@NotNull GrExpression expression) {
    visitElement(expression);
  }

  public void visitCallExpression(@NotNull GrCallExpression callExpression) {
    visitExpression(callExpression);
  }

  public void visitMethodCallExpression(@NotNull GrMethodCallExpression methodCallExpression) {
    visitCallExpression(methodCallExpression);
  }

  public void visitNewExpression(@NotNull GrNewExpression newExpression) {
    visitCallExpression(newExpression);
  }

  public void visitApplicationStatement(@NotNull GrApplicationStatement applicationStatement) {
    visitStatement(applicationStatement);
  }

  public void visitArrayDeclaration(@NotNull GrArrayDeclaration arrayDeclaration) {
    visitElement(arrayDeclaration);
  }

  public void visitCommandArguments(@NotNull GrCommandArgumentList argumentList) {
    visitArgumentList(argumentList);
  }

  public void visitElvisExpression(@NotNull GrElvisExpression expression) {
    visitConditionalExpression(expression);
  }

  public void visitConditionalExpression(@NotNull GrConditionalExpression expression) {
    visitExpression(expression);
  }

  public void visitAssignmentExpression(@NotNull GrAssignmentExpression expression) {
    visitExpression(expression);
  }

  public void visitTupleAssignmentExpression(@NotNull GrTupleAssignmentExpression expression) {
    visitExpression(expression);
  }

  public void visitBinaryExpression(@NotNull GrBinaryExpression expression) {
    visitExpression(expression);
  }

  public void visitInExpression(@NotNull GrInExpression expression) {
    visitBinaryExpression(expression);
  }

  public void visitUnaryExpression(@NotNull GrUnaryExpression expression) {
    visitExpression(expression);
  }

  public void visitRegexExpression(@NotNull GrRegex expression) {
    visitGStringExpression(expression);
  }

  public void visitLiteralExpression(@NotNull GrLiteral literal) {
    visitExpression(literal);
  }

  public void visitGStringExpression(@NotNull GrString gstring) {
    visitLiteralExpression(gstring);
  }

  public void visitReferenceExpression(@NotNull GrReferenceExpression referenceExpression) {
    visitExpression(referenceExpression);
  }

  public void visitCastExpression(@NotNull GrTypeCastExpression typeCastExpression) {
    visitExpression(typeCastExpression);
  }

  public void visitSafeCastExpression(@NotNull GrSafeCastExpression typeCastExpression) {
    visitExpression(typeCastExpression);
  }

  public void visitInstanceofExpression(@NotNull GrInstanceOfExpression expression) {
    visitExpression(expression);
  }

  public void visitBuiltinTypeClassExpression(@NotNull GrBuiltinTypeClassExpression expression) {
    visitExpression(expression);
  }

  public void visitParenthesizedExpression(@NotNull GrParenthesizedExpression expression) {
    visitExpression(expression);
  }

  public void visitPropertySelection(@NotNull GrPropertySelection expression) {
    visitExpression(expression);
  }

  public void visitIndexProperty(@NotNull GrIndexProperty expression) {
    visitExpression(expression);
  }

  public void visitArgumentList(@NotNull GrArgumentList list) {
    visitElement(list);
  }

  public void visitNamedArgument(@NotNull GrNamedArgument argument) {
    visitElement(argument);
  }

  public void visitArgumentLabel(@NotNull GrArgumentLabel argumentLabel) {
    visitElement(argumentLabel);
  }

  public void visitListOrMap(@NotNull GrListOrMap listOrMap) {
    visitExpression(listOrMap);
  }

  public void visitTypeElement(@NotNull GrTypeElement typeElement) {
    visitElement(typeElement);
  }

  public void visitArrayTypeElement(@NotNull GrArrayTypeElement typeElement) {
    visitTypeElement(typeElement);
  }

  public void visitBuiltinTypeElement(@NotNull GrBuiltInTypeElement typeElement) {
    visitTypeElement(typeElement);
  }

  public void visitClassTypeElement(@NotNull GrClassTypeElement typeElement) {
    visitTypeElement(typeElement);
  }

  public void visitDisjunctionTypeElement(@NotNull GrDisjunctionTypeElement disjunctionTypeElement) {
    visitTypeElement(disjunctionTypeElement);
  }

  public void visitCodeReferenceElement(@NotNull GrCodeReferenceElement refElement) {
    visitElement(refElement);
  }

  public void visitTypeDefinition(@NotNull GrTypeDefinition typeDefinition) {
    visitElement(typeDefinition);
  }

  public void visitClassDefinition(@NotNull GrClassDefinition classDefinition) {
    visitTypeDefinition(classDefinition);
  }

  public void visitEnumDefinition(@NotNull GrEnumTypeDefinition enumDefinition) {
    visitTypeDefinition(enumDefinition);
  }

  public void visitInterfaceDefinition(@NotNull GrInterfaceDefinition interfaceDefinition) {
    visitTypeDefinition(interfaceDefinition);
  }

  public void visitAnonymousClassDefinition(@NotNull GrAnonymousClassDefinition anonymousClassDefinition) {
    visitTypeDefinition(anonymousClassDefinition);
  }

  public void visitAnnotationTypeDefinition(@NotNull GrAnnotationTypeDefinition annotationTypeDefinition) {
    visitTypeDefinition(annotationTypeDefinition);
  }

  public void visitTraitDefinition(@NotNull GrTraitTypeDefinition traitTypeDefinition) {
    visitTypeDefinition(traitTypeDefinition);
  }

  public void visitExtendsClause(@NotNull GrExtendsClause extendsClause) {
    visitElement(extendsClause);
  }

  public void visitImplementsClause(@NotNull GrImplementsClause implementsClause) {
    visitElement(implementsClause);
  }

  public void visitTypeArgumentList(@NotNull GrTypeArgumentList typeArgumentList) {
    visitElement(typeArgumentList);
  }

  public void visitWildcardTypeArgument(@NotNull GrWildcardTypeArgument wildcardTypeArgument) {
    visitElement(wildcardTypeArgument);
  }

  public void visitAnnotationMethod(@NotNull GrAnnotationMethod annotationMethod) {
    visitMethod(annotationMethod);
  }

  public void visitMethod(@NotNull GrMethod method) {
    visitElement(method);
  }

  public void visitDocMethodReference(@NotNull GrDocMethodReference reference){
    visitElement(reference);
  }

  public void visitDocFieldReference(@NotNull GrDocFieldReference reference){
    visitElement(reference);
  }

  public void visitDocMethodParameterList(@NotNull GrDocMethodParams params){
    visitElement(params);
  }

  public void visitDocMethodParameter(@NotNull GrDocMethodParameter parameter){
    visitElement(parameter);
  }

  public void visitConstructorInvocation(@NotNull GrConstructorInvocation invocation) {
    visitStatement(invocation);
  }

  public void visitThrowsClause(@NotNull GrThrowsClause throwsClause) {
    visitElement(throwsClause);
  }

  public void visitAnnotationArgumentList(@NotNull GrAnnotationArgumentList annotationArgumentList) {
    visitElement(annotationArgumentList);
  }

  public void visitAnnotationArrayInitializer(@NotNull GrAnnotationArrayInitializer arrayInitializer) {
    visitElement(arrayInitializer);
  }

  public void visitAnnotationNameValuePair(@NotNull GrAnnotationNameValuePair nameValuePair) {
    visitElement(nameValuePair);
  }

  public void visitAnnotation(@NotNull GrAnnotation annotation) {
    visitElement(annotation);
  }

  public void visitParameterList(@NotNull GrParameterList parameterList) {
    visitElement(parameterList);
  }

  public void visitParameter(@NotNull GrParameter parameter) {
    visitVariable(parameter);
  }

  public void visitField(@NotNull GrField field) {
    visitVariable(field);
  }

  public void visitTypeDefinitionBody(@NotNull GrTypeDefinitionBody typeDefinitionBody) {
    visitElement(typeDefinitionBody);
  }

  public void visitEnumDefinitionBody(@NotNull GrEnumDefinitionBody enumDefinitionBody) {
    visitTypeDefinitionBody(enumDefinitionBody);
  }

  public void visitIfStatement(@NotNull GrIfStatement ifStatement) {
    visitStatement(ifStatement);
  }

  public void visitForStatement(@NotNull GrForStatement forStatement) {
    visitStatement(forStatement);
  }

  public void visitWhileStatement(@NotNull GrWhileStatement whileStatement) {
    visitStatement(whileStatement);
  }

  public void visitSwitchStatement(@NotNull GrSwitchStatement switchStatement) {
    visitStatement(switchStatement);
  }

  public void visitCaseSection(@NotNull GrCaseSection caseSection) {
    visitElement(caseSection);
  }

  public void visitCaseLabel(@NotNull GrCaseLabel caseLabel) {
    visitElement(caseLabel);
  }

  public void visitForInClause(@NotNull GrForInClause forInClause) {
    visitForClause(forInClause);
  }

  public void visitForClause(@NotNull GrForClause forClause) {
    visitElement(forClause);
  }

  public void visitTraditionalForClause(@NotNull GrTraditionalForClause forClause) {
    visitForClause(forClause);
  }

  public void visitTryStatement(@NotNull GrTryCatchStatement tryCatchStatement) {
    visitStatement(tryCatchStatement);
  }

  public void visitBlockStatement(@NotNull GrBlockStatement blockStatement) {
    visitStatement(blockStatement);
  }

  public void visitCatchClause(@NotNull GrCatchClause catchClause) {
    visitElement(catchClause);
  }

  public void visitDocComment(@NotNull GrDocComment comment) {
    visitElement(comment);
  }

  public void visitDocTag(@NotNull GrDocTag docTag) {
    visitElement(docTag);
  }

  public void visitFinallyClause(@NotNull GrFinallyClause catchClause) {
    visitElement(catchClause);
  }

  public void visitSynchronizedStatement(@NotNull GrSynchronizedStatement synchronizedStatement) {
    visitStatement(synchronizedStatement);
  }

  public void visitVariableDeclaration(@NotNull GrVariableDeclaration variableDeclaration) {
    visitStatement(variableDeclaration);
  }

  public void visitVariable(@NotNull GrVariable variable) {
    visitElement(variable);
  }

  public void visitModifierList(@NotNull GrModifierList modifierList) {
    visitElement(modifierList);
  }

  public void visitRangeExpression(@NotNull GrRangeExpression range) {
    visitBinaryExpression(range);
  }

  public void visitGStringInjection(@NotNull GrStringInjection injection) {
    visitElement(injection);
  }

  public void visitTypeParameterList(@NotNull GrTypeParameterList list) {
    visitElement(list);
  }

  public  void visitClassInitializer(@NotNull GrClassInitializer initializer) {
    visitElement(initializer);
  }

  public void visitTypeParameter(@NotNull GrTypeParameter typeParameter) {
    visitTypeDefinition(typeParameter);
  }

  public void visitTuple(@NotNull GrTuple tuple) {
    visitElement(tuple);
  }

  public void visitSpreadArgument(@NotNull GrSpreadArgument spreadArgument) {
    visitExpression(spreadArgument);
  }
}
