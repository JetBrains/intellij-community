/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.siyeh.ig.bugs;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInspection.dataFlow.*;
import com.intellij.codeInspection.dataFlow.instructions.MethodCallInstruction;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * @author Bas Leijdekkers
 */
public class OptionalGetWithoutIsPresentInspection extends BaseInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("optional.get.without.is.present.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    final PsiClass aClass = (PsiClass)infos[0];
    return InspectionGadgetsBundle.message("optional.get.without.is.present.problem.descriptor", aClass.getName());
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new OptionalGetWithoutIsPresentVisitor();
  }

  private static class OptionalGetWithoutIsPresentVisitor extends BaseInspectionVisitor {

    private final Set<PsiMethodCallExpression> seen = new THashSet<>();

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (seen.contains(expression)) {
        return;
      }
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      final String name = methodExpression.getReferenceName();
      if (!isOptionalGetMethodName(name)) {
        return;
      }
      final PsiExpression qualifier = ParenthesesUtils.stripParentheses(methodExpression.getQualifierExpression());
      if (qualifier == null) {
        return;
      }
      final PsiType type = qualifier.getType();
      if (!TypeUtils.isOptional(type)) {
        return;
      }
      PsiElement context = PsiTreeUtil.getParentOfType(expression, PsiMember.class, PsiLambdaExpression.class);
      if (context instanceof PsiMethod) {
        context = ((PsiMethod)context).getBody();
      }
      else if (context instanceof PsiClassInitializer) {
        context = ((PsiClassInitializer)context).getBody();
      }
      else if (context instanceof PsiField) {
        context = ((PsiField)context).getInitializer();
      }
      if (context == null) {
        return;
      }
      final StandardDataFlowRunner dfaRunner = new StandardDataFlowRunner(false, true, isOnTheFly());
      dfaRunner.analyzeMethod(context, new StandardInstructionVisitor() {

        @Override
        public DfaInstructionState[] visitMethodCall(MethodCallInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
          final int length = instruction.getArgs().length;
          if (length != 0) {
            return super.visitMethodCall(instruction, runner, memState);
          }
          final DfaValue qualifierValue = memState.peek();
          final DfaInstructionState[] states = super.visitMethodCall(instruction, runner, memState);

          final PsiCall callExpression = instruction.getCallExpression();
          if (callExpression instanceof PsiMethodCallExpression) {
            final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)callExpression;
            if (isCallOnSameQualifier(methodCallExpression, qualifierValue, qualifier)) {
              final PsiMethod targetMethod = instruction.getTargetMethod();
              if (targetMethod != null) {
                final PsiClass aClass = targetMethod.getContainingClass();
                if (TypeUtils.isOptional(aClass)) {
                  final String methodName = targetMethod.getName();
                  if ("isPresent".equals(methodName)) {
                    memState.pop();
                    memState.push(runner.getFactory().getConstFactory().getFalse());
                  }
                  else if (isOptionalGetMethodName(methodName)) {
                    seen.add(methodCallExpression);
                    registerMethodCallError(methodCallExpression, aClass);
                  }
                }
              }
            }
          }
          return states;
        }
      });
    }

    private static boolean isCallOnSameQualifier(PsiMethodCallExpression methodCallExpression,
                                                 DfaValue qualifierValue, PsiExpression qualifier) {
      if ((qualifier instanceof PsiReferenceExpression) && qualifierValue instanceof DfaVariableValue &&
          ((DfaVariableValue)qualifierValue).getPsiVariable().equals(((PsiReferenceExpression)qualifier).resolve())) {
        return true;
      }
      final PsiReferenceExpression referenceExpression = methodCallExpression.getMethodExpression();
      final PsiExpression qualifierExpression = referenceExpression.getQualifierExpression();
      return qualifierExpression != null && PsiEquivalenceUtil.areElementsEquivalent(qualifier, qualifierExpression);
    }

    private static boolean isOptionalGetMethodName(String name) {
      return "get".equals(name) || "getAsDouble".equals(name) || "getAsInt".equals(name) || "getAsLong".equals(name);
    }
  }
}
