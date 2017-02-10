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
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.dataFlow.*;
import com.intellij.codeInspection.dataFlow.instructions.MethodCallInstruction;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ThreeState;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

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
    final PsiClassType aClass = (PsiClassType)infos[0];
    final ThreeState state = ((ThreeState)infos[1]);
    if (state == ThreeState.NO) {
      return InspectionGadgetsBundle.message("optional.get.definitely.absent.problem.descriptor", aClass.rawType().getClassName());
    }
    else {
      return InspectionGadgetsBundle.message("optional.get.without.is.present.problem.descriptor", aClass.rawType().getClassName());
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new OptionalGetWithoutIsPresentVisitor();
  }

  private static class OptionalGetWithoutIsPresentVisitor extends BaseInspectionVisitor {

    private final Map<PsiMethodCallExpression, ThreeState> seen = new HashMap<>();

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      final String name = methodExpression.getReferenceName();
      if (!isOptionalGetMethodName(name)) return;
      final PsiExpression qualifier = ParenthesesUtils.stripParentheses(methodExpression.getQualifierExpression());
      if (qualifier == null) return;
      final PsiType type = qualifier.getType();
      if (!TypeUtils.isOptional(type)) return;
      ThreeState state = seen.get(expression);
      if (state == null) {
        PsiElement context = getContext(expression);
        if (context == null) return;
        analyze(context);
      }
      state = seen.get(expression);
      if (state != null && state != ThreeState.YES) {
        registerMethodCallError(expression, type, state);
      }
    }

    private void analyze(PsiElement context) {
      final DataFlowRunner dfaRunner = new StandardDataFlowRunner(false, true, isOnTheFly()) {
        private final DfaOptionalValue myPresentValue = new DfaOptionalValue(getFactory(), true);
        private final DfaOptionalValue myAbsentValue = new DfaOptionalValue(getFactory(), false);

        @NotNull
        @Override
        protected DfaMemoryState createMemoryState() {
          return new OptionalMemoryState(getFactory(), myPresentValue, myAbsentValue);
        }
      };
      dfaRunner.analyzeMethod(context, new StandardInstructionVisitor() {
        @Override
        public DfaInstructionState[] visitMethodCall(MethodCallInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
          PsiMethodCallExpression call = ObjectUtils.tryCast(instruction.getCallExpression(), PsiMethodCallExpression.class);
          if (call != null) {
            String methodName = call.getMethodExpression().getReferenceName();
            PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
            OptionalMemoryState optionalMemState = (OptionalMemoryState)memState;
            if (qualifier != null && TypeUtils.isOptional(qualifier.getType())) {
              if ("isPresent".equals(methodName)) {
                DfaValue qualifierValue = optionalMemState.peek();
                ThreeState state = optionalMemState.checkOptional(qualifierValue);
                if(state == ThreeState.UNSURE) {
                  DfaInstructionState[] states = super.visitMethodCall(instruction, runner, memState);
                  if(states.length != 1) return states;
                  OptionalMemoryState trueState = (OptionalMemoryState)states[0].getMemoryState();
                  trueState.pop();
                  OptionalMemoryState falseState = trueState.createCopy();
                  trueState.push(optionalMemState.getBoolean(true));
                  trueState.applyIsPresentCheck(true, qualifierValue);
                  falseState.push(optionalMemState.getBoolean(false));
                  falseState.applyIsPresentCheck(false, qualifierValue);
                  return new DfaInstructionState[] {states[0], new DfaInstructionState(states[0].getInstruction(), falseState)};
                } else {
                  return replaceResult(instruction, runner, memState, optionalMemState.getBoolean(state.toBoolean()));
                }
              }
              else if (isOptionalGetMethodName(methodName)) {
                ThreeState state = optionalMemState.checkOptional(memState.peek());
                seen.merge(call, state, (s1, s2) -> s1 == s2 ? s1 : ThreeState.UNSURE);
              }
            }
            if ("of".equals(methodName)) {
              PsiMethod method = call.resolveMethod();
              if (method != null && TypeUtils.isOptional(method.getContainingClass())) {
                return replaceResult(instruction, runner, memState, optionalMemState.getOptional(true));
              }
            }
            if ("ofNullable".equals(methodName)) {
              PsiMethod method = call.resolveMethod();
              if (method != null &&
                  TypeUtils.isOptional(method.getContainingClass()) &&
                  call.getArgumentList().getExpressions().length == 1) {
                if(memState.isNotNull(memState.peek())) {
                  return replaceResult(instruction, runner, memState, optionalMemState.getOptional(true));
                }
              }
            }
            if ("empty".equals(methodName)) {
              PsiMethod method = call.resolveMethod();
              if (method != null && TypeUtils.isOptional(method.getContainingClass())) {
                return replaceResult(instruction, runner, memState, optionalMemState.getOptional(false));
              }
            }
          }
          return super.visitMethodCall(instruction, runner, memState);
        }

        private DfaInstructionState[] replaceResult(MethodCallInstruction instruction,
                                                    DataFlowRunner runner,
                                                    DfaMemoryState memState,
                                                    DfaValue result) {
          DfaInstructionState[] states = super.visitMethodCall(instruction, runner, memState);
          memState.pop();
          memState.push(result);
          return states;
        }
      });
    }

    @Nullable
    private static PsiElement getContext(PsiMethodCallExpression expression) {
      PsiElement context = PsiTreeUtil.getParentOfType(expression, PsiMember.class, PsiLambdaExpression.class);
      if (context instanceof PsiMethod) {
        return ((PsiMethod)context).getBody();
      }
      else if (context instanceof PsiClassInitializer) {
        return ((PsiClassInitializer)context).getBody();
      }
      else if (context instanceof PsiField) {
        return ((PsiField)context).getInitializer();
      }
      return context;
    }

    private static boolean isOptionalGetMethodName(String name) {
      return "get".equals(name) || "getAsDouble".equals(name) || "getAsInt".equals(name) || "getAsLong".equals(name);
    }

    static class OptionalMemoryState extends DfaMemoryStateImpl {
      private final DfaOptionalValue myPresentValue;
      private final DfaOptionalValue myAbsentValue;

      public OptionalMemoryState(DfaValueFactory factory,
                                 DfaOptionalValue presentValue,
                                 DfaOptionalValue absentValue) {
        super(factory);
        myPresentValue = presentValue;
        myAbsentValue = absentValue;
      }

      public OptionalMemoryState(OptionalMemoryState state) {
        super(state);
        myPresentValue = state.myPresentValue;
        myAbsentValue = state.myAbsentValue;
      }

      public DfaValue getBoolean(boolean val) {
        return val ? getFactory().getConstFactory().getTrue() : getFactory().getConstFactory().getFalse();
      }

      public DfaValue getOptional(boolean present) {
        return present ? myPresentValue : myAbsentValue;
      }

      @NotNull
      @Override
      protected DfaVariableState createVariableState(@NotNull DfaVariableValue var) {
        return new ValuableDataFlowRunner.ValuableDfaVariableState(var);
      }

      public ThreeState checkOptional(DfaValue value) {
        if (value instanceof DfaVariableValue) {
          DfaVariableValue var = (DfaVariableValue)value;
          DfaValue newCond = getVariableState(var).getValue();
          if (newCond == null) {
            return checkOptional(getVariableState(var.createNegated()).getValue());
          }
          return checkOptional(newCond);
        }
        return value instanceof DfaOptionalValue ? ThreeState.fromBoolean(((DfaOptionalValue)value).myPresent) : ThreeState.UNSURE;
      }

      void applyIsPresentCheck(boolean present, DfaValue qualifier) {
        if (qualifier instanceof DfaVariableValue) {
          DfaVariableValue optionalVar = (DfaVariableValue)qualifier;
          setVariableState(optionalVar, getVariableState(optionalVar).withValue(getOptional(present)));
        }
      }

      @NotNull
      @Override
      public OptionalMemoryState createCopy() {
        return new OptionalMemoryState(this);
      }
    }
  }

  static class DfaOptionalValue extends DfaValue {
    final boolean myPresent;

    protected DfaOptionalValue(DfaValueFactory factory, boolean isPresent) {
      super(factory);
      myPresent = isPresent;
    }

    public String toString() {
      return myPresent ? "Optional with value" : "Empty optional";
    }
  }
}
