/*
 * Copyright 2007-2016 Bas Leijdekkers
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
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.value.DfaRelationValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class SuspiciousComparatorCompareInspection extends BaseInspection {

  @Override
  @Nls
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("suspicious.comparator.compare.display.name");
  }

  @NotNull
  @Override
  public String getShortName() {
    return "ComparatorMethodParameterNotUsed";
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return (String)infos[0];
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SuspiciousComparatorCompareVisitor();
  }

  private static class SuspiciousComparatorCompareVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(PsiMethod method) {
      super.visitMethod(method);
      if (!MethodUtils.isComparatorCompare(method) || ControlFlowUtils.methodAlwaysThrowsException(method)) {
        return;
      }
      check(method);
    }

    @Override
    public void visitLambdaExpression(PsiLambdaExpression lambda) {
      super.visitLambdaExpression(lambda);
      final PsiClass functionalInterface = PsiUtil.resolveClassInType(lambda.getFunctionalInterfaceType());
      if (functionalInterface == null || !CommonClassNames.JAVA_UTIL_COMPARATOR.equals(functionalInterface.getQualifiedName()) ||
          ControlFlowUtils.lambdaExpressionAlwaysThrowsException(lambda)) {
        return;
      }
      check(lambda);
    }

    private void check(PsiParameterListOwner owner) {
      PsiParameterList parameterList = owner.getParameterList();
      PsiElement body = owner.getBody();
      if (body == null || parameterList.getParametersCount() != 2) return;
      // comparator like "(a, b) -> 0" fulfills the comparator contract, so no need to warn its parameters are not used
      if (body instanceof PsiExpression && ExpressionUtils.isZero((PsiExpression)body)) return;
      if (body instanceof PsiCodeBlock) {
        PsiStatement statement = ControlFlowUtils.getOnlyStatementInBlock((PsiCodeBlock)body);
        if (statement instanceof PsiReturnStatement && ExpressionUtils.isZero(((PsiReturnStatement)statement).getReturnValue())) return;
      }
      PsiMethodCallExpression soleCall = ObjectUtils.tryCast(LambdaUtil.extractSingleExpressionFromBody(body), PsiMethodCallExpression.class);
      if (soleCall != null) {
        MethodContract contract = ContainerUtil.getOnlyItem(JavaMethodContractUtil.getMethodCallContracts(soleCall));
        if (contract != null && contract.isTrivial() && contract.getReturnValue().isFail()) return;
      }
      PsiParameter[] parameters = parameterList.getParameters();
      checkParameterList(parameters, body);
      checkReflexivity(owner, parameters, body);
    }

    private void checkParameterList(PsiParameter[] parameters, PsiElement context) {
      final ParameterAccessVisitor visitor = new ParameterAccessVisitor(parameters);
      context.accept(visitor);
      for (PsiParameter unusedParameter : visitor.getUnusedParameters()) {
        registerVariableError(unusedParameter, InspectionGadgetsBundle.message(
          "suspicious.comparator.compare.descriptor.parameter.not.used"));
      }
    }

    private void checkReflexivity(PsiParameterListOwner owner, PsiParameter[] parameters, PsiElement body) {
      StandardDataFlowRunner runner = new StandardDataFlowRunner(false, body) {
        @NotNull
        @Override
        protected DfaMemoryState createMemoryState() {
          DfaMemoryState state = super.createMemoryState();
          DfaVariableValue var1 = getFactory().getVarFactory().createVariableValue(parameters[0]);
          DfaVariableValue var2 = getFactory().getVarFactory().createVariableValue(parameters[1]);
          DfaValue condition = getFactory().createCondition(var1, DfaRelationValue.RelationType.EQ, var2);
          state.applyCondition(condition);
          return state;
        }
      };
      ComparatorVisitor visitor = new ComparatorVisitor(owner);
      if (runner.analyzeMethod(body, visitor) != RunnerResult.OK) return;
      if (visitor.myRange.contains(0) || visitor.myContexts.isEmpty()) return;
      PsiElement context = null;
      if (visitor.myContexts.size() == 1) {
        context = visitor.myContexts.iterator().next();
      }
      else {
        PsiElement commonParent = PsiTreeUtil.findCommonParent(visitor.myContexts.toArray(PsiElement.EMPTY_ARRAY));
        if (commonParent instanceof PsiExpression) {
          context = commonParent;
        } else {
          PsiParameterListOwner parent = PsiTreeUtil.getParentOfType(body, PsiMethod.class, PsiLambdaExpression.class);
          if (parent instanceof PsiMethod) {
            context = ((PsiMethod)parent).getNameIdentifier();
          }
          else if (parent instanceof PsiLambdaExpression) {
            context = parent.getParameterList();
          }
        }
      }
      registerError(context != null ? context : body,
                    InspectionGadgetsBundle.message("suspicious.comparator.compare.descriptor.non.reflexive"));
    }

    private static class ComparatorVisitor extends StandardInstructionVisitor {
      private final PsiParameterListOwner myOwner;
      private final Set<PsiElement> myContexts = new HashSet<>();
      LongRangeSet myRange = LongRangeSet.empty();

      public ComparatorVisitor(PsiParameterListOwner owner) {
        myOwner = owner;
      }

      @Override
      protected void checkReturnValue(@NotNull DfaValue value,
                                      @NotNull PsiExpression expression,
                                      @NotNull PsiParameterListOwner owner,
                                      @NotNull DfaMemoryState state) {
        if (owner != myOwner) return;
        myContexts.add(expression);
        LongRangeSet range = state.getValueFact(value, DfaFactType.RANGE);
        myRange = range == null ? LongRangeSet.all() : myRange.union(range);
      }
    }

    private static class ParameterAccessVisitor extends JavaRecursiveElementWalkingVisitor {

      private final Set<PsiParameter> parameters;

      private ParameterAccessVisitor(@NotNull PsiParameter[] parameters) {
        this.parameters = new HashSet<>(Arrays.asList(parameters));
      }

      @Override
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);
        if (expression.getQualifierExpression() != null) {
          // optimization
          // references to parameters are never qualified
          return;
        }
        final PsiElement target = expression.resolve();
        if (!(target instanceof PsiParameter)) {
          return;
        }
        final PsiParameter parameter = (PsiParameter)target;
        parameters.remove(parameter);
        if (parameters.isEmpty()) {
          stopWalking();
        }
      }

      private Collection<PsiParameter> getUnusedParameters() {
        return Collections.unmodifiableSet(parameters);
      }
    }
  }
}