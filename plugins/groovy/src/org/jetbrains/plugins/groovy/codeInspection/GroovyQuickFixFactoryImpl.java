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
package org.jetbrains.plugins.groovy.codeInspection;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiType;
import org.jetbrains.plugins.groovy.annotator.intentions.*;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DynamicMethodFix;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DynamicPropertyFix;
import org.jetbrains.plugins.groovy.codeInspection.bugs.AddClassToExtendsFix;
import org.jetbrains.plugins.groovy.codeInspection.bugs.AddMethodFix;
import org.jetbrains.plugins.groovy.codeInspection.confusing.ReplaceWithImportFix;
import org.jetbrains.plugins.groovy.codeInspection.cs.GrReplaceMultiAssignmentFix;
import org.jetbrains.plugins.groovy.codeInspection.local.RemoveUnusedGrParameterFix;
import org.jetbrains.plugins.groovy.codeInspection.naming.RenameFix;
import org.jetbrains.plugins.groovy.dsl.InvestigateFix;
import org.jetbrains.plugins.groovy.lang.GrCreateClassKind;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrVariableDeclarationOwner;

public class GroovyQuickFixFactoryImpl extends GroovyQuickFixFactory {
  @Override
  public IntentionAction createDynamicMethodFix(GrReferenceExpression expression, PsiType[] types) {
    return new DynamicMethodFix(expression, types);
  }

  @Override
  public IntentionAction createDynamicPropertyFix(GrReferenceExpression expression) {
    return new DynamicPropertyFix(expression);
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
    return referenceName == null ? null : new CreateFieldFromUsageFix(expr, referenceName);
  }

  @Override
  public IntentionAction createCreateGetterFromUsageFix(GrReferenceExpression expr, PsiClass aClass) {
    return new CreateGetterFromUsageFix(expr, aClass);
  }

  @Override
  public IntentionAction createCreateSetterFromUsageFix(GrReferenceExpression expr) {
    return new CreateSetterFromUsageFix(expr);
  }

  @Override
  public IntentionAction createCreateMethodFromUsageFix(GrReferenceExpression expr) {
    return new CreateMethodFromUsageFix(expr);
  }

  @Override
  public IntentionAction createCreateLocalVariableFromUsageFix(GrReferenceExpression expr, GrVariableDeclarationOwner owner) {
    return new CreateLocalVariableFromUsageFix(expr, owner);
  }

  @Override
  public IntentionAction createCreateParameterFromUsageFix(GrReferenceExpression expr) {
    return new CreateParameterFromUsageFix(expr);
  }

  @Override
  public IntentionAction createGroovyStaticImportMethodFix(GrMethodCall parent) {
    return new GroovyStaticImportMethodFix(parent);
  }

  @Override
  public GroovyFix createRenameFix() {
    return new RenameFix();
  }

  @Override
  public GroovyFix createReplaceWithImportFix() {
    return new ReplaceWithImportFix();
  }

  @Override
  public LocalQuickFix createGrMoveToDirFix(String actual) {
    return new GrMoveToDirFix(actual);
  }

  @Override
  public LocalQuickFix createCreateFieldFromConstructorLabelFix(GrTypeDefinition element, GrNamedArgument argument) {
    return new CreateFieldFromConstructorLabelFix(element, argument);
  }

  @Override
  public LocalQuickFix createDynamicPropertyFix(GrArgumentLabel label, PsiClass element) {
    return new DynamicPropertyFix(label, element);
  }

  @Override
  public GroovyFix createAddMethodFix(String methodName, GrTypeDefinition aClass) {
    return new AddMethodFix(methodName, aClass);
  }

  @Override
  public GroovyFix createAddClassToExtendsFix(GrTypeDefinition aClass, String comparable) {
    return new AddClassToExtendsFix(aClass, comparable);
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
  public GroovyFix createMultipleAssignmentFix(int size) {
    return new GrReplaceMultiAssignmentFix(size);
  }
}
