// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.NlsContexts.DetailedDescription;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiType;
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

public abstract class GroovyQuickFixFactory {
  public static GroovyQuickFixFactory getInstance() {
    return ApplicationManager.getApplication().getService(GroovyQuickFixFactory.class);
  }

  public abstract IntentionAction createDynamicMethodFix(GrReferenceExpression expression, PsiType[] types);

  public abstract IntentionAction createDynamicPropertyFix(GrReferenceExpression expression);

  public abstract IntentionAction createGroovyAddImportAction(GrReferenceElement element);

  public abstract IntentionAction createClassFromNewAction(GrNewExpression parent);

  public abstract IntentionAction createClassFixAction(GrReferenceElement element, GrCreateClassKind anInterface);

  public abstract IntentionAction createCreateFieldFromUsageFix(GrReferenceExpression expr);

  public abstract IntentionAction createCreateGetterFromUsageFix(GrReferenceExpression expr, PsiClass aClass);

  public abstract IntentionAction createCreateSetterFromUsageFix(GrReferenceExpression expr);

  public abstract IntentionAction createCreateMethodFromUsageFix(GrReferenceExpression expr);

  public abstract IntentionAction createCreateLocalVariableFromUsageFix(GrReferenceExpression expr, GrVariableDeclarationOwner owner);

  public abstract IntentionAction createCreateParameterFromUsageFix(GrReferenceExpression expr);

  public abstract IntentionAction createGroovyStaticImportMethodFix(GrMethodCall parent);

  public abstract GroovyFix createRenameFix();

  public abstract GroovyFix createReplaceWithImportFix();

  public abstract LocalQuickFix createGrMoveToDirFix(@NlsSafe String actual);

  public abstract LocalQuickFix createCreateFieldFromConstructorLabelFix(GrTypeDefinition element, GrNamedArgument argument);

  public abstract LocalQuickFix createDynamicPropertyFix(GrArgumentLabel label, PsiClass element);

  public abstract GroovyFix createAddMethodFix(@NlsSafe String methodName, GrTypeDefinition aClass);

  public abstract GroovyFix createAddClassToExtendsFix(GrTypeDefinition aClass, @NlsSafe String comparable);

  public abstract IntentionAction createOptimizeImportsFix(boolean onTheFly);

  public abstract IntentionAction createRemoveUnusedGrParameterFix(GrParameter parameter);

  public abstract IntentionAction createInvestigateFix(@DetailedDescription String reason);

  public abstract GroovyFix createMultipleAssignmentFix(int size);

  public abstract GroovyFix createSpreadArgumentFix(int size);

  public abstract GroovyFix createMapConstructorFix();
}
