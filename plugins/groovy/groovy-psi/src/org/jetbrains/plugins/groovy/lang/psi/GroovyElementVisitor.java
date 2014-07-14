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
package org.jetbrains.plugins.groovy.lang.psi;

import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.*;
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
  public void visitElement(GroovyPsiElement element) {
  }

  public void visitFile(GroovyFileBase file) {
    visitElement(file);
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

  public void visitOpenBlock(GrOpenBlock block) {
    visitElement(block);
  }

  public void visitEnumConstants(GrEnumConstantList enumConstantsSection) {
    visitElement(enumConstantsSection);
  }

  public void visitEnumConstant(GrEnumConstant enumConstant) {
    visitField(enumConstant);
  }

  public void visitImportStatement(GrImportStatement importStatement) {
    visitElement(importStatement);
  }

  public void visitBreakStatement(GrBreakStatement breakStatement) {
    visitFlowInterruptStatement(breakStatement);
  }

  public void visitContinueStatement(GrContinueStatement continueStatement) {
    visitFlowInterruptStatement(continueStatement);
  }

  public void visitFlowInterruptStatement(GrFlowInterruptingStatement statement) {
    visitStatement(statement);
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
    visitArgumentList(argumentList);
  }

  public void visitElvisExpression(GrElvisExpression expression) {
    visitConditionalExpression(expression);
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

  public void visitRegexExpression(GrRegex expression) {
    visitGStringExpression(expression);
  }

  public void visitLiteralExpression(GrLiteral literal) {
    visitExpression(literal);
  }

  public void visitGStringExpression(GrString gstring) {
    visitLiteralExpression(gstring);
  }

  public void visitReferenceExpression(GrReferenceExpression referenceExpression) {
    visitExpression(referenceExpression);
  }

  public void visitCastExpression(GrTypeCastExpression typeCastExpression) {
    visitExpression(typeCastExpression);
  }

  public void visitSafeCastExpression(GrSafeCastExpression typeCastExpression) {
    visitExpression(typeCastExpression);
  }

  public void visitInstanceofExpression(GrInstanceOfExpression expression) {
    visitExpression(expression);
  }

  public void visitBuiltinTypeClassExpression(GrBuiltinTypeClassExpression expression) {
    visitExpression(expression);
  }

  public void visitParenthesizedExpression(GrParenthesizedExpression expression) {
    visitExpression(expression);
  }

  public void visitPropertySelection(GrPropertySelection expression) {
    visitExpression(expression);
  }

  public void visitIndexProperty(GrIndexProperty expression) {
    visitExpression(expression);
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

  public void visitTypeElement(GrTypeElement typeElement) {
    visitElement(typeElement);
  }

  public void visitArrayTypeElement(GrArrayTypeElement typeElement) {
    visitTypeElement(typeElement);
  }

  public void visitBuiltinTypeElement(GrBuiltInTypeElement typeElement) {
    visitTypeElement(typeElement);
  }

  public void visitClassTypeElement(GrClassTypeElement typeElement) {
    visitTypeElement(typeElement);
  }

  public void visitDisjunctionTypeElement(GrDisjunctionTypeElement disjunctionTypeElement) {
    visitTypeElement(disjunctionTypeElement);
  }

  public void visitCodeReferenceElement(GrCodeReferenceElement refElement) {
    visitElement(refElement);
  }

  public void visitTypeDefinition(GrTypeDefinition typeDefinition) {
    visitElement(typeDefinition);
  }

  public void visitClassDefinition(GrClassDefinition classDefinition) {
    visitTypeDefinition(classDefinition);
  }

  public void visitEnumDefinition(GrEnumTypeDefinition enumDefinition) {
    visitTypeDefinition(enumDefinition);
  }

  public void visitInterfaceDefinition(GrInterfaceDefinition interfaceDefinition) {
    visitTypeDefinition(interfaceDefinition);
  }

  public void visitAnonymousClassDefinition(GrAnonymousClassDefinition anonymousClassDefinition) {
    visitTypeDefinition(anonymousClassDefinition);
  }

  public void visitAnnotationTypeDefinition(GrAnnotationTypeDefinition annotationTypeDefinition) {
    visitTypeDefinition(annotationTypeDefinition);
  }

  public void visitTraitDefinition(GrTraitTypeDefinition traitTypeDefinition) {
    visitTypeDefinition(traitTypeDefinition);
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

  public void visitAnnotationMethod(GrAnnotationMethod annotationMethod) {
    visitMethod(annotationMethod);
  }

  public void visitMethod(GrMethod method) {
    visitElement(method);
  }

  public void visitDocMethodReference(GrDocMethodReference reference){
    visitElement(reference);
  }

  public void visitDocFieldReference(GrDocFieldReference reference){
    visitElement(reference);
  }

  public void visitDocMethodParameterList(GrDocMethodParams params){
    visitElement(params);
  }

  public void visitDocMethodParameter(GrDocMethodParameter parameter){
    visitElement(parameter);
  }

  public void visitConstructorInvocation(GrConstructorInvocation invocation) {
    visitStatement(invocation);
  }

  public void visitThrowsClause(GrThrowsClause throwsClause) {
    visitElement(throwsClause);
  }

  public void visitAnnotationArgumentList(GrAnnotationArgumentList annotationArgumentList) {
    visitElement(annotationArgumentList);
  }

  public void visitAnnotationArrayInitializer(GrAnnotationArrayInitializer arrayInitializer) {
    visitElement(arrayInitializer);
  }

  public void visitAnnotationNameValuePair(GrAnnotationNameValuePair nameValuePair) {
    visitElement(nameValuePair);
  }

  public void visitAnnotation(GrAnnotation annotation) {
    visitElement(annotation);
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

  public void visitEnumDefinitionBody(GrEnumDefinitionBody enumDefinitionBody) {
    visitTypeDefinitionBody(enumDefinitionBody);
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

  public void visitSwitchStatement(GrSwitchStatement switchStatement) {
    visitStatement(switchStatement);
  }

  public void visitCaseSection(GrCaseSection caseSection) {
    visitElement(caseSection);
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

  public void visitTraditionalForClause(GrTraditionalForClause forClause) {
    visitForClause(forClause);
  }

  public void visitTryStatement(GrTryCatchStatement tryCatchStatement) {
    visitStatement(tryCatchStatement);
  }

  public void visitBlockStatement(GrBlockStatement blockStatement) {
    visitStatement(blockStatement);
  }

  public void visitCatchClause(GrCatchClause catchClause) {
    visitElement(catchClause);
  }

  public void visitDocComment(GrDocComment comment) {
    visitElement(comment);
  }

  public void visitDocTag(GrDocTag docTag) {
    visitElement(docTag);
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

  public void visitRangeExpression(GrRangeExpression range) {
    visitBinaryExpression(range);
  }

  public void visitGStringInjection(GrStringInjection injection) {
    visitElement(injection);
  }

  public void visitTypeParameterList(GrTypeParameterList list) {
    visitElement(list);
  }

  public  void visitClassInitializer(GrClassInitializer initializer) {
    visitElement(initializer);
  }

  public void visitTypeParameter(GrTypeParameter typeParameter) {
    visitTypeDefinition(typeParameter);
  }

  public void visitTupleExpression(GrTupleExpression tupleExpression) {
    visitExpression(tupleExpression);
  }

  public void visitSpreadArgument(GrSpreadArgument spreadArgument) {
    visitExpression(spreadArgument);
  }
}
