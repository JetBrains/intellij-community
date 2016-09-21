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

import com.intellij.codeInspection.dataFlow.*;
import com.intellij.codeInspection.dataFlow.instructions.*;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

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

    @Override
    public void visitMethod(PsiMethod method) {
      check(method.getBody());
    }

    @Override
    public void visitClassInitializer(PsiClassInitializer initializer) {
      check(initializer.getBody());
    }

    @Override
    public void visitField(PsiField field) {
      check(field.getInitializer());
    }

    private void check(PsiElement element) {
      if (!containsOptionalGetCall(element)) {
        return;
      }
      final StandardDataFlowRunner dfaRunner = new StandardDataFlowRunner(false, true, isOnTheFly());
      dfaRunner.analyzeMethod(element, new InstructionVisitor() {
        @Override
        public DfaInstructionState[] visitAssign(AssignInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
          DfaValue dfaSource = memState.pop();
          DfaValue dfaDest = memState.pop();
          if (dfaDest instanceof DfaVariableValue) {
            DfaVariableValue var = (DfaVariableValue)dfaDest;
            final PsiModifierListOwner psi = var.getPsiVariable();
            if (!(psi instanceof PsiField) || !psi.hasModifierProperty(PsiModifier.VOLATILE)) {
              memState.setVarValue(var, dfaSource);
            }
          }
          memState.push(dfaDest);
          return nextInstruction(instruction, runner, memState);
        }

        @Override
        public DfaInstructionState[] visitMethodCall(MethodCallInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
          final DfaInstructionState[] states = super.visitMethodCall(instruction, runner, memState);

          final PsiMethod targetMethod = instruction.getTargetMethod();
          if (targetMethod != null) {
            final PsiClass aClass = targetMethod.getContainingClass();
            if (TypeUtils.isOptional(aClass)) {
              final String name = targetMethod.getName();
              if (name.equals("isPresent")) {
                memState.pop();
                memState.push(runner.getFactory().getConstFactory().getFalse());
              }
              else if (name.equals("get") || name.equals("getAsDouble") || name.equals("getAsInt") || name.equals("getAsLong")) {
                final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)instruction.getCallExpression();
                if (methodCallExpression != null) {
                  registerMethodCallError(methodCallExpression, aClass);
                }
              }
            }
          }
          return states;
        }
      });
    }

    private static boolean containsOptionalGetCall(PsiElement element) {
      if (element == null) return false;
      final OptionalGetCallChecker checker = new OptionalGetCallChecker();
      element.acceptChildren(checker);
      return checker.containsOptionalGetCall();
    }

    private static class OptionalGetCallChecker extends JavaRecursiveElementWalkingVisitor {
      private boolean result = false;

      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression expression) {
        if (result) {
          return;
        }
        super.visitMethodCallExpression(expression);
        final PsiReferenceExpression methodExpression = expression.getMethodExpression();
        final String name = methodExpression.getReferenceName();
        if (!"get".equals(name) && !"getAsDouble".equals(name) && !"getAsInt".equals(name) && !"getAsLong".equals(name)) {
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
        result = true;
      }

      public boolean containsOptionalGetCall() {
        return result;
      }
    }
  }
}
