// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import org.jetbrains.plugins.groovy.annotator.intentions.CreateClassFix;
import org.jetbrains.plugins.groovy.annotator.intentions.GrCreateFieldFromConstructorLabelFix;
import org.jetbrains.plugins.groovy.annotator.intentions.GrCreateFieldFromUsageFix;
import org.jetbrains.plugins.groovy.annotator.intentions.GrCreateGetterFromUsageFix;
import org.jetbrains.plugins.groovy.annotator.intentions.GrCreateLocalVariableFromUsageFix;
import org.jetbrains.plugins.groovy.annotator.intentions.GrCreateMethodFromUsageFix;
import org.jetbrains.plugins.groovy.annotator.intentions.GrCreateParameterFromUsageFix;
import org.jetbrains.plugins.groovy.annotator.intentions.GrCreateSetterFromUsageFix;
import org.jetbrains.plugins.groovy.annotator.intentions.GrMoveToDirFix;
import org.jetbrains.plugins.groovy.annotator.intentions.GroovyAddImportAction;
import org.jetbrains.plugins.groovy.annotator.intentions.GroovyStaticImportMethodFix;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DynamicMethodFix;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DynamicPropertyFromRefFix;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.GrDynamicPropertyFromLabelFix;
import org.jetbrains.plugins.groovy.annotator.intentions.elements.GrReplaceWithQualifiedExpressionFix;
import org.jetbrains.plugins.groovy.annotator.intentions.elements.annotation.MapConstructorAttributesFix;
import org.jetbrains.plugins.groovy.codeInspection.bugs.GrAddClassToExtendsFix;
import org.jetbrains.plugins.groovy.codeInspection.bugs.GrAddMethodFix;
import org.jetbrains.plugins.groovy.codeInspection.bugs.GrAddMissingCaseSectionsFix;
import org.jetbrains.plugins.groovy.codeInspection.confusing.GrReplaceWithImportFix;
import org.jetbrains.plugins.groovy.codeInspection.cs.GrReplaceMultiAssignmentFix;
import org.jetbrains.plugins.groovy.codeInspection.cs.SpreadArgumentFix;
import org.jetbrains.plugins.groovy.codeInspection.local.RemoveUnusedGrParameterFix;
import org.jetbrains.plugins.groovy.codeInspection.naming.GrRenameFix;
import org.jetbrains.plugins.groovy.dsl.InvestigateFix;
import org.jetbrains.plugins.groovy.lang.GrCreateClassKind;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrSwitchElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrVariableDeclarationOwner;

import java.util.List;

public final class GroovyQuickFixFactoryImpl extends GroovyQuickFixFactory {
  @Override
  public IntentionAction createDynamicMethodFix(GrReferenceExpression expression, PsiType[] types) {
    return new DynamicMethodFix(expression, types);
  }

  @Override
  public IntentionAction createDynamicPropertyFix(GrReferenceExpression expression) {
    return new DynamicPropertyFromRefFix(expression);
  }

  @Override
  public IntentionAction createGroovyAddImportAction(GrReferenceElement element) {
    return new GroovyAddImportAction(element);
  }

  @Override
  public IntentionAction createClassFromNewAction(GrNewExpression parent) {
    return CreateClassFix.createClassFromNewAction(parent);
  }

  @Override
  public IntentionAction createClassFixAction(GrReferenceElement element, GrCreateClassKind anInterface) {
    return CreateClassFix.createClassFixAction(element, anInterface);
  }

  @Override
  public IntentionAction createCreateFieldFromUsageFix(GrReferenceExpression expr) {
    final String referenceName = expr.getReferenceName();
    return referenceName == null ? null : new GrCreateFieldFromUsageFix(expr, referenceName);
  }

  @Override
  public IntentionAction createCreateGetterFromUsageFix(GrReferenceExpression expr, PsiClass aClass) {
    return new GrCreateGetterFromUsageFix(expr);
  }

  @Override
  public IntentionAction createCreateSetterFromUsageFix(GrReferenceExpression expr) {
    return new GrCreateSetterFromUsageFix(expr);
  }

  @Override
  public IntentionAction createCreateMethodFromUsageFix(GrReferenceExpression expr) {
    return new GrCreateMethodFromUsageFix(expr);
  }

  @Override
  public IntentionAction createCreateLocalVariableFromUsageFix(GrReferenceExpression expr, GrVariableDeclarationOwner owner) {
    return new GrCreateLocalVariableFromUsageFix(expr, owner);
  }

  @Override
  public IntentionAction createCreateParameterFromUsageFix(GrReferenceExpression expr) {
    return new GrCreateParameterFromUsageFix(expr);
  }

  @Override
  public IntentionAction createGroovyStaticImportMethodFix(GrMethodCall parent) {
    return new GroovyStaticImportMethodFix(parent);
  }

  @Override
  public GroovyFix createRenameFix() {
    return new GrRenameFix();
  }

  @Override
  public LocalQuickFix createReplaceWithImportFix() {
    return new GrReplaceWithImportFix();
  }

  @Override
  public LocalQuickFix createGrMoveToDirFix(String actual) {
    return new GrMoveToDirFix(actual);
  }

  @Override
  public LocalQuickFix createCreateFieldFromConstructorLabelFix(GrTypeDefinition element, GrNamedArgument argument) {
    return new GrCreateFieldFromConstructorLabelFix(element, argument);
  }

  @Override
  public LocalQuickFix createDynamicPropertyFix(GrArgumentLabel label, PsiClass element) {
    return new GrDynamicPropertyFromLabelFix(label, element);
  }

  @Override
  public GroovyFix createAddMethodFix(String methodName, GrTypeDefinition aClass) {
    return new GrAddMethodFix(methodName, aClass);
  }

  @Override
  public GroovyFix createAddClassToExtendsFix(GrTypeDefinition aClass, String comparable) {
    return new GrAddClassToExtendsFix(aClass, comparable);
  }

  @Override
  public IntentionAction createOptimizeImportsFix(boolean onTheFly) {
    return new GroovyOptimizeImportsFix(onTheFly);
  }

  @Override
  public IntentionAction createRemoveUnusedGrParameterFix(GrParameter parameter) {
    return new RemoveUnusedGrParameterFix(parameter);
  }

  @Override
  public IntentionAction createInvestigateFix(String reason) {
    return new InvestigateFix(reason);
  }

  @Override
  public LocalQuickFix createMultipleAssignmentFix(int size) {
    return new GrReplaceMultiAssignmentFix(size);
  }

  @Override
  public LocalQuickFix createSpreadArgumentFix(int size) {
    return new SpreadArgumentFix(size);
  }

  @Override
  public LocalQuickFix createMapConstructorFix() {
    return new MapConstructorAttributesFix();
  }

  @Override
  public LocalQuickFix createQualifyExpressionFix() {
    return new GrReplaceWithQualifiedExpressionFix();
  }

  @Override
  public LocalQuickFix createAddMissingCasesFix(List<? extends PsiElement> expressions, GrSwitchElement switchElement) {
    return new GrAddMissingCaseSectionsFix(expressions, switchElement);
  }
}
