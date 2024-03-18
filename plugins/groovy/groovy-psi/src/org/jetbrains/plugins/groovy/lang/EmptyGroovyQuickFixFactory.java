// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixes;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import org.jetbrains.plugins.groovy.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.codeInspection.GroovyQuickFixFactory;
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

public class EmptyGroovyQuickFixFactory extends GroovyQuickFixFactory {
  @Override
  public IntentionAction createDynamicMethodFix(GrReferenceExpression expression, PsiType[] types) {
    return QuickFixes.EMPTY_ACTION;
  }

  @Override
  public IntentionAction createDynamicPropertyFix(GrReferenceExpression expression) {
    return QuickFixes.EMPTY_ACTION;
  }

  @Override
  public IntentionAction createGroovyAddImportAction(GrReferenceElement element) {
    return QuickFixes.EMPTY_ACTION;
  }

  @Override
  public IntentionAction createClassFromNewAction(GrNewExpression parent) {
    return QuickFixes.EMPTY_ACTION;
  }

  @Override
  public IntentionAction createClassFixAction(GrReferenceElement element, GrCreateClassKind anInterface) {
    return QuickFixes.EMPTY_ACTION;
  }

  @Override
  public IntentionAction createCreateFieldFromUsageFix(GrReferenceExpression expr) {
    return QuickFixes.EMPTY_ACTION;
  }

  @Override
  public IntentionAction createCreateGetterFromUsageFix(GrReferenceExpression expr, PsiClass aClass) {
    return QuickFixes.EMPTY_ACTION;
  }

  @Override
  public IntentionAction createCreateSetterFromUsageFix(GrReferenceExpression expr) {
    return QuickFixes.EMPTY_ACTION;
  }

  @Override
  public IntentionAction createCreateMethodFromUsageFix(GrReferenceExpression expr) {
    return QuickFixes.EMPTY_ACTION;
  }

  @Override
  public IntentionAction createCreateLocalVariableFromUsageFix(GrReferenceExpression expr, GrVariableDeclarationOwner owner) {
    return QuickFixes.EMPTY_ACTION;
  }

  @Override
  public IntentionAction createCreateParameterFromUsageFix(GrReferenceExpression expr) {
    return QuickFixes.EMPTY_ACTION;
  }

  @Override
  public IntentionAction createGroovyStaticImportMethodFix(GrMethodCall parent) {
    return QuickFixes.EMPTY_ACTION;
  }

  @Override
  public GroovyFix createRenameFix() {
    return GroovyFix.EMPTY_FIX;
  }

  @Override
  public LocalQuickFix createReplaceWithImportFix() {
    return GroovyFix.EMPTY_FIX;
  }

  @Override
  public LocalQuickFix createGrMoveToDirFix(String actual) {
    return QuickFixes.EMPTY_ACTION;
  }

  @Override
  public LocalQuickFix createCreateFieldFromConstructorLabelFix(GrTypeDefinition element, GrNamedArgument argument) {
    return QuickFixes.EMPTY_ACTION;
  }

  @Override
  public LocalQuickFix createDynamicPropertyFix(GrArgumentLabel label, PsiClass element) {
    return QuickFixes.EMPTY_ACTION;
  }

  @Override
  public GroovyFix createAddMethodFix(String methodName, GrTypeDefinition aClass) {
    return GroovyFix.EMPTY_FIX;
  }

  @Override
  public GroovyFix createAddClassToExtendsFix(GrTypeDefinition aClass, String comparable) {
    return GroovyFix.EMPTY_FIX;
  }

  @Override
  public IntentionAction createOptimizeImportsFix(boolean onTheFly) {
    return QuickFixes.EMPTY_ACTION;
  }

  @Override
  public IntentionAction createRemoveUnusedGrParameterFix(GrParameter parameter) {
    return QuickFixes.EMPTY_ACTION;
  }

  @Override
  public IntentionAction createInvestigateFix(String reason) {
    return QuickFixes.EMPTY_ACTION;
  }

  @Override
  public LocalQuickFix createMultipleAssignmentFix(int size) {
    return GroovyFix.EMPTY_FIX;
  }

  @Override
  public LocalQuickFix createSpreadArgumentFix(int size) {
    return GroovyFix.EMPTY_FIX;
  }

  @Override
  public LocalQuickFix createMapConstructorFix() {
    return GroovyFix.EMPTY_FIX;
  }

  @Override
  public LocalQuickFix createQualifyExpressionFix() {
    return GroovyFix.EMPTY_FIX;
  }

  @Override
  public LocalQuickFix createAddMissingCasesFix(List<? extends PsiElement> expressions, GrSwitchElement switchElement) {
    return null;
  }
}
