/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixes;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiType;
import org.jetbrains.plugins.groovy.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.codeInspection.GroovyQuickFixFactory;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrVariableDeclarationOwner;

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
  public GroovyFix createReplaceWithImportFix() {
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
  public GroovyFix createMultipleAssignmentFix(int size) {
    return GroovyFix.EMPTY_FIX;
  }
}
