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
package org.jetbrains.plugins.groovy.lang;

import com.intellij.codeInsight.daemon.impl.quickfix.CreateClassKind;
import com.intellij.codeInsight.intention.IntentionAction;
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

public class CoreGroovyQuickFixFactory extends GroovyQuickFixFactory {
  @Override
  public IntentionAction createDynamicMethodFix(GrReferenceExpression expression, PsiType[] types) {
    throw new UnsupportedOperationException();
  }

  @Override
  public IntentionAction createDynamicPropertyFix(GrReferenceExpression expression) {
    throw new UnsupportedOperationException();
  }

  @Override
  public IntentionAction createGroovyAddImportAction(GrReferenceElement element) {
    throw new UnsupportedOperationException();
  }

  @Override
  public IntentionAction createClassFromNewAction(GrNewExpression parent) {
    throw new UnsupportedOperationException();
  }

  @Override
  public IntentionAction createClassFixAction(GrReferenceElement element, CreateClassKind anInterface) {
    throw new UnsupportedOperationException();
  }

  @Override
  public IntentionAction createCreateFieldFromUsageFix(GrReferenceExpression expr) {
    throw new UnsupportedOperationException();
  }

  @Override
  public IntentionAction createCreateGetterFromUsageFix(GrReferenceExpression expr, PsiClass aClass) {
    throw new UnsupportedOperationException();
  }

  @Override
  public IntentionAction createCreateSetterFromUsageFix(GrReferenceExpression expr) {
    throw new UnsupportedOperationException();
  }

  @Override
  public IntentionAction createCreateMethodFromUsageFix(GrReferenceExpression expr) {
    throw new UnsupportedOperationException();
  }

  @Override
  public IntentionAction createCreateLocalVariableFromUsageFix(GrReferenceExpression expr, GrVariableDeclarationOwner owner) {
    throw new UnsupportedOperationException();
  }

  @Override
  public IntentionAction createCreateParameterFromUsageFix(GrReferenceExpression expr) {
    throw new UnsupportedOperationException();
  }

  @Override
  public IntentionAction createGroovyStaticImportMethodFix(GrMethodCall parent) {
    throw new UnsupportedOperationException();
  }

  @Override
  public GroovyFix createRenameFix() {
    throw new UnsupportedOperationException();
  }

  @Override
  public GroovyFix createReplaceWithImportFix() {
    throw new UnsupportedOperationException();
  }

  @Override
  public LocalQuickFix createGrMoveToDirFix(String actual) {
    throw new UnsupportedOperationException();
  }

  @Override
  public LocalQuickFix createCreateFieldFromConstructorLabelFix(GrTypeDefinition element, GrNamedArgument argument) {
    throw new UnsupportedOperationException();
  }

  @Override
  public LocalQuickFix createDynamicPropertyFix(GrArgumentLabel label, PsiClass element) {
    throw new UnsupportedOperationException();
  }

  @Override
  public GroovyFix createAddMethodFix(String methodName, GrTypeDefinition aClass) {
    throw new UnsupportedOperationException();
  }

  @Override
  public GroovyFix createAddClassToExtendsFix(GrTypeDefinition aClass, String comparable) {
    throw new UnsupportedOperationException();
  }

  @Override
  public IntentionAction createOptimizeImportsFix(boolean onTheFly) {
    throw new UnsupportedOperationException();
  }

  @Override
  public IntentionAction createRemoveUnusedGrParameterFix(GrParameter parameter) {
    throw new UnsupportedOperationException();
  }

  @Override
  public IntentionAction createInvestigateFix(String reason) {
    throw new UnsupportedOperationException();
  }
}
