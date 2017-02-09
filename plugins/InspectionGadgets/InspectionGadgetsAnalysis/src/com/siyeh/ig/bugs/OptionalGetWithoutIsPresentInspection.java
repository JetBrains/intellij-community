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
import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ThreeState;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import gnu.trove.TIntObjectHashMap;
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
        private final OptionalValueFactory myOptionalFactory = new OptionalValueFactory(getFactory());

        @NotNull
        @Override
        protected DfaMemoryState createMemoryState() {
          return new OptionalMemoryState(getFactory(), myOptionalFactory);
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
                DfaValue result = optionalMemState.createIsPresentCheckResult(memState.peek());
                return replaceResult(instruction, runner, memState, result);
              }
              else if (isOptionalGetMethodName(methodName)) {
                ThreeState state = optionalMemState.checkOptional(memState.peek());
                seen.merge(call, state, (s1, s2) -> s1 == s2 ? s1 : ThreeState.UNSURE);
              }
            }
            if ("of".equals(methodName)) {
              PsiMethod method = call.resolveMethod();
              if (method != null && TypeUtils.isOptional(method.getContainingClass())) {
                return replaceResult(instruction, runner, memState, optionalMemState.getOptionalFactory().getOptional(true));
              }
            }
            if ("ofNullable".equals(methodName)) {
              PsiMethod method = call.resolveMethod();
              if (method != null &&
                  TypeUtils.isOptional(method.getContainingClass()) &&
                  call.getArgumentList().getExpressions().length == 1) {
                if(memState.isNotNull(memState.peek())) {
                  return replaceResult(instruction, runner, memState, optionalMemState.getOptionalFactory().getOptional(true));
                }
              }
            }
            if ("empty".equals(methodName)) {
              PsiMethod method = call.resolveMethod();
              if (method != null && TypeUtils.isOptional(method.getContainingClass())) {
                return replaceResult(instruction, runner, memState, optionalMemState.getOptionalFactory().getOptional(false));
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

    static class OptionalValueFactory {
      private final DfaOptionalValue myPresentOptional, myAbsentOptional;
      private final DfaValueFactory myFactory;
      private final TIntObjectHashMap<IsPresentCheck> myPresentChecks = new TIntObjectHashMap<>();

      OptionalValueFactory(DfaValueFactory factory) {
        myPresentOptional = new DfaOptionalValue(factory, true);
        myAbsentOptional = new DfaOptionalValue(factory, false);
        myFactory = factory;
      }

      IsPresentCheck getIsPresentCheck(DfaValue optional) {
        int id = optional.getID();
        IsPresentCheck check = myPresentChecks.get(id);
        if(check == null) {
          myPresentChecks.put(id, check = new IsPresentCheck(myFactory, optional, null));
        }
        return check;
      }

      DfaOptionalValue getOptional(boolean present) {
        return present ? myPresentOptional : myAbsentOptional;
      }
    }

    static class OptionalMemoryState extends DfaMemoryStateImpl {
      private final OptionalValueFactory myOptionalFactory;

      protected OptionalMemoryState(DfaValueFactory factory,
                                    OptionalValueFactory optionalFactory) {
        super(factory);
        myOptionalFactory = optionalFactory;
      }

      protected OptionalMemoryState(OptionalMemoryState toCopy) {
        super(toCopy);
        myOptionalFactory = toCopy.myOptionalFactory;
      }

      public OptionalValueFactory getOptionalFactory() {
        return myOptionalFactory;
      }

      @Override
      public boolean applyCondition(final DfaValue dfaCond) {
        if (dfaCond instanceof DfaRelationValue) {
          DfaRelationValue relation = (DfaRelationValue)dfaCond;
          if (relation.isEquality() || relation.isNonEquality()) {
            DfaValue left = relation.getLeftOperand();
            DfaValue right = relation.getRightOperand();
            DfaConstValue constValue = null;
            DfaValue nonConst = null;
            if (left instanceof DfaConstValue) {
              constValue = (DfaConstValue)left;
              nonConst = right;
            }
            else if (right instanceof DfaConstValue) {
              constValue = (DfaConstValue)right;
              nonConst = left;
            }
            if (constValue != null) {
              IsPresentCheck check = unwrapValue(nonConst, IsPresentCheck.class);
              Object value = constValue.getValue();
              if (value instanceof Boolean && check != null) {
                boolean present = ((Boolean)value).booleanValue() ^ relation.isNonEquality();
                applyIsPresentCheck(present ? check : check.createNegated());
              }
            }
          }
        }
        IsPresentCheck check = unwrapValue(dfaCond, IsPresentCheck.class);
        if (check != null) {
          return applyIsPresentCheck(check);
        }
        return super.applyCondition(dfaCond);
      }

      @NotNull
      DfaValue createIsPresentCheckResult(DfaValue qualifierValue) {
        ThreeState state = checkOptional(qualifierValue);
        switch (state) {
          case YES:
            return getFactory().getConstFactory().getTrue();
          case NO:
            return getFactory().getConstFactory().getFalse();
          case UNSURE:
            return myOptionalFactory.getIsPresentCheck(qualifierValue);
        }
        throw new IllegalStateException();
      }

      @NotNull
      @Override
      protected DfaVariableState createVariableState(@NotNull DfaVariableValue var) {
        if (var.isNegated()) {
          DfaVariableState negatedState = getVariableState(var.createNegated());
          DfaValue negatedValue = negatedState.getValue();
          if (negatedValue != null) {
            return negatedState.withValue(negatedValue.createNegated());
          }
        }
        return new ValuableDataFlowRunner.ValuableDfaVariableState(var);
      }

      private <T extends DfaValue> T unwrapValue(DfaValue dfaCond, Class<T> aClass) {
        if (dfaCond instanceof DfaVariableValue) {
          DfaVariableValue var = (DfaVariableValue)dfaCond;
          DfaValue newCond = getVariableState(var).getValue();
          if (newCond == null) {
            newCond = getVariableState(var.createNegated()).getValue();
            T check = unwrapValue(newCond, aClass);
            return check == null ? null : ObjectUtils.tryCast(check.createNegated(), aClass);
          }
          return unwrapValue(newCond, aClass);
        }
        return ObjectUtils.tryCast(dfaCond, aClass);
      }

      private boolean applyIsPresentCheck(IsPresentCheck check) {
        DfaOptionalValue optional = unwrapValue(check.myOptional, DfaOptionalValue.class);
        if (optional != null) {
          return optional.myPresent == check.myNegated;
        }
        if (check.myOptional instanceof DfaVariableValue) {
          DfaVariableValue optionalVar = (DfaVariableValue)check.myOptional;
          setVariableState(optionalVar, getVariableState(optionalVar)
            .withValue(myOptionalFactory.getOptional(!check.myNegated)));
        }
        return true;
      }

      @NotNull
      @Override
      public DfaMemoryStateImpl createCopy() {
        return new OptionalMemoryState(this);
      }

      public ThreeState checkOptional(DfaValue value) {
        DfaOptionalValue optional = unwrapValue(value, DfaOptionalValue.class);
        return optional == null ? ThreeState.UNSURE : ThreeState.fromBoolean(optional.myPresent);
      }
    }
  }

  static class IsPresentCheck extends DfaValue implements DfaComparableValue {
    final @NotNull DfaValue myOptional;
    final boolean myNegated;
    final IsPresentCheck myInverted;

    protected IsPresentCheck(DfaValueFactory factory, @NotNull DfaValue optional, IsPresentCheck positiveCheck) {
      super(factory);
      myOptional = optional;
      myNegated = positiveCheck != null;
      myInverted = positiveCheck == null ? new IsPresentCheck(factory, optional, this) : positiveCheck;
    }

    @Override
    public IsPresentCheck createNegated() {
      return myInverted;
    }

    public String toString() {
      return (myNegated ? "!" : "") + myOptional + ".isPresent()";
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
