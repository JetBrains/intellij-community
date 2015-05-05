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
package org.jetbrains.plugins.groovy.lang.flow.value;

import com.intellij.codeInspection.dataFlow.DfaPsiUtil;
import com.intellij.codeInspection.dataFlow.Nullness;
import com.intellij.codeInspection.dataFlow.value.DfaConstValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrConstructorInvocation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyConstantExpressionEvaluator;


public class GrDfaValueFactory extends DfaValueFactory {

  private final GrDfaVariableValue.FactoryImpl myVarFactory;
  private final GrDfaConstValueFactory myConstFactory;
  private final PsiElementFactory myElementFactory;

  public GrDfaValueFactory(Project project, boolean unknownMembersAreNullable) {
    super(true, unknownMembersAreNullable);
    myVarFactory = new GrDfaVariableValue.FactoryImpl(this);
    myConstFactory = new GrDfaConstValueFactory(this);
    myElementFactory = PsiElementFactory.SERVICE.getInstance(project);
  }

  public DfaValue createValue(GrExpression expression) {
    if (expression == null) return null;

    if (expression instanceof GrParenthesizedExpression) {
      return createValue(((GrParenthesizedExpression)expression).getOperand());
    }

    //if (expression instanceof GrIndexProperty) {
    //final GrExpression arrayExpression = ((GrIndexProperty)expression).getInvokedExpression();
    //DfaValue qualifier = createValue(expression);
    //if (qualifier instanceof DfaVariableValue) {
    //PsiVariable indexVar = ((GrIndexProperty)expression).getExpressionArguments()[0];
    //if (indexVar != null) {
    //  return myFactory.getVarFactory().createVariableValue(indexVar, expression.getType(), false, (DfaVariableValue)qualifier);
    //}
    //}
    //}

    if (expression instanceof GrMethodCall) {
      final GrExpression invokedExpression = ((GrMethodCall)expression).getInvokedExpression();
      if (invokedExpression instanceof GrReferenceExpression) {
        return createReferenceValue((GrReferenceExpression)invokedExpression);
      }
      else {
        return null;
      }
    }

    if (expression instanceof GrReferenceExpression) {
      return createReferenceValue((GrReferenceExpression)expression);
    }

    if (expression instanceof GrLiteral) {
      return createLiteralValue((GrLiteral)expression);
    }

    if (expression instanceof GrNewExpression || expression instanceof GrClosableBlock || expression instanceof GrListOrMap) {
      return createTypeValue(expression.getType(), Nullness.NOT_NULL);
    }

    final Object value = GroovyConstantExpressionEvaluator.evaluate(expression);
    final PsiType type = expression.getType();
    if (value != null && type != null) {
      if (value instanceof String) {
        return createTypeValue(type, Nullness.NOT_NULL); // Non-null string literal.
      }
      return getConstFactory().createFromValue(value, type, null);
    }

    if (expression instanceof GrConstructorInvocation) {
      final GrReferenceExpression qualifier = ((GrConstructorInvocation)expression).getInvokedExpression();
      final PsiElement target = qualifier.resolve();
      if (target instanceof PsiClass) {
        return getVarFactory().createVariableValue((PsiModifierListOwner)target, null, false, null);
      }
    }

    return null;
  }

  private DfaValue createReferenceValue(@NotNull GrReferenceExpression refExpr) {

    final GroovyResolveResult resolveResult = refExpr.advancedResolve();
    final PsiElement resolved = resolveResult.getElement();
    final PsiModifierListOwner var = resolved instanceof PsiModifierListOwner ? (PsiModifierListOwner)resolved : null;
    //getAccessedVariableOrGetter(resolved);
    if (var == null) {
      return null;
    }

    if (resolved instanceof PsiClass) {
      final PsiSubstitutor substitutor = resolveResult.getSubstitutor();
      final PsiClassType type = myElementFactory.createType((PsiClass)resolved, substitutor);
      return createTypeValue(type, Nullness.NOT_NULL);
    }

    if (!var.hasModifierProperty(PsiModifier.VOLATILE)) {
      if (var instanceof PsiVariable && var.hasModifierProperty(PsiModifier.FINAL)) {
        DfaValue constValue = getConstFactory().create((PsiVariable)var);
        if (constValue != null) return constValue;
      }

      PsiType type = refExpr.getNominalType();
      if (type == null) {
        type = PsiType.getJavaLangObject(refExpr.getManager(), refExpr.getResolveScope());
      }

      if (isEffectivelyUnqualified(refExpr)) {
        return getVarFactory().createVariableValue(var, type, false, null);
      }

      DfaValue qualifierValue = createValue(refExpr.getQualifierExpression());
      if (qualifierValue instanceof DfaVariableValue) {
        return getVarFactory().createVariableValue(var, type, false, (DfaVariableValue)qualifierValue);
      }
    }

    final PsiType type = refExpr.getType();
    return createTypeValue(type, DfaPsiUtil.getElementNullability(type, var));
  }

  public DfaValue createValue(Object value) {
    return new DfaConstValue(value, this, null);
  }

  public DfaValue createLiteralValue(GrLiteral literal) {
    if (literal instanceof GrString || literal.getValue() instanceof String) {
      return createTypeValue(literal.getType(), Nullness.NOT_NULL); // Non-null string literal.
    }
    return getConstFactory().create(literal);
  }

  @NotNull
  @Override
  public GrDfaVariableValue.FactoryImpl getVarFactory() {
    return myVarFactory;
  }

  @NotNull
  @Override
  public GrDfaConstValueFactory getConstFactory() {
    return myConstFactory;
  }

  public static boolean isEffectivelyUnqualified(GrReferenceExpression refExpression) {
    GrExpression qualifier = refExpression.getQualifierExpression();
    if (qualifier == null) {
      return true;
    }
    if (qualifier instanceof GrConstructorInvocation) {
      final GrReferenceExpression thisQualifier = ((GrConstructorInvocation)qualifier).getInvokedExpression();
      final PsiClass innerMostClass = PsiTreeUtil.getParentOfType(refExpression, PsiClass.class);
      if (innerMostClass == thisQualifier.resolve()) {
        return true;
      }
    }
    return false;
  }
}
