/*
 * Copyright 2008-2017 Bas Leijdekkers
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
package com.siyeh.ig.performance;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.RedundantCastUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class KeySetIterationMayUseEntrySetInspection extends BaseInspection {

  @Override
  @NotNull
  @Nls
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "key.set.iteration.may.use.entry.set.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "key.set.iteration.may.use.entry.set.problem.descriptor");
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new KeySetIterationMapUseEntrySetFix();
  }

  private static class KeySetIterationMapUseEntrySetFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("key.set.iteration.may.use.entry.set.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiElement parent = ParenthesesUtils.getParentSkipParentheses(element);
      if (!(parent instanceof PsiForeachStatement)) {
        return;
      }
      final PsiElement map;
      if (element instanceof PsiReferenceExpression) {
        final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)element;
        final PsiElement target = referenceExpression.resolve();
        if (!(target instanceof PsiVariable)) {
          return;
        }
        final PsiVariable variable = (PsiVariable)target;
        final PsiExpression initializer = variable.getInitializer();
        if (!(initializer instanceof PsiMethodCallExpression)) {
          return;
        }
        final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)initializer;
        final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
        final PsiExpression qualifier = methodExpression.getQualifierExpression();
        if (!(qualifier instanceof PsiReferenceExpression)) {
          return;
        }
        final PsiReferenceExpression reference = (PsiReferenceExpression)qualifier;
        map = reference.resolve();
        final String qualifierText = qualifier.getText();
        PsiReplacementUtil.replaceExpression(referenceExpression, qualifierText + ".entrySet()");
      }
      else if (element instanceof PsiMethodCallExpression) {
        final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)element;
        final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
        final PsiExpression qualifier = methodExpression.getQualifierExpression();
        if (!(qualifier instanceof PsiReferenceExpression)) {
          return;
        }
        final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)qualifier;
        map = referenceExpression.resolve();
        final String qualifierText = qualifier.getText();
        PsiReplacementUtil.replaceExpression(methodCallExpression, qualifierText + ".entrySet()");
      }
      else {
        return;
      }
      final PsiForeachStatement foreachStatement = (PsiForeachStatement)parent;
      final PsiExpression iteratedValue = foreachStatement.getIteratedValue();
      if (iteratedValue == null) {
        return;
      }
      final PsiType type = iteratedValue.getType();
      if (!(type instanceof PsiClassType)) {
        return;
      }
      final PsiClassType classType = (PsiClassType)type;
      final PsiType[] parameterTypes = classType.getParameters();
      PsiType parameterType = parameterTypes.length == 1 ? parameterTypes[0] : null;
      boolean insertCast = false;
      if (parameterType == null) {
        parameterType = TypeUtils.getObjectType(foreachStatement);
        insertCast = true;
      }
      final PsiParameter parameter = foreachStatement.getIterationParameter();
      final String variableName = createNewVariableName(foreachStatement, parameterType);
      if (insertCast) {
        replaceParameterAccess(parameter, "((Map.Entry)" + variableName + ')', map, foreachStatement);
      }
      else {
        replaceParameterAccess(parameter, variableName, map, foreachStatement);
      }
      final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
      final PsiParameter newParameter = factory.createParameter(variableName, parameterType);
      if (parameter.hasModifierProperty(PsiModifier.FINAL)) {
        final PsiModifierList modifierList = newParameter.getModifierList();
        if (modifierList != null) {
          modifierList.setModifierProperty(PsiModifier.FINAL, true);
        }
      }
      parameter.replace(newParameter);
    }

    private static void replaceParameterAccess(PsiParameter parameter,
                                               @NonNls String variableName,
                                               PsiElement map,
                                               PsiElement context) {
      final ParameterAccessCollector collector = new ParameterAccessCollector(parameter, map);
      context.accept(collector);
      final List<PsiExpression> accesses = collector.getParameterAccesses();
      final String keyAccess = '(' + parameter.getType().getCanonicalText() + ')' + variableName + ".getKey()";
      for (PsiExpression access : accesses) {
        if (access instanceof PsiMethodCallExpression) {
          PsiReplacementUtil.replaceExpression(access, variableName + ".getValue()");
        }
        else {
          final PsiTypeCastExpression typeCastExpression = (PsiTypeCastExpression)
            PsiReplacementUtil.replaceExpressionAndShorten(access, keyAccess);
          if (RedundantCastUtil.isCastRedundant(typeCastExpression)) {
            final PsiExpression operand = typeCastExpression.getOperand();
            assert operand != null;
            typeCastExpression.replace(operand);
          }
        }
      }
    }

    private static String createNewVariableName(@NotNull PsiElement scope, @NotNull PsiType type) {
      final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(scope.getProject());
      final SuggestedNameInfo suggestions = codeStyleManager.suggestVariableName(VariableKind.LOCAL_VARIABLE, null, null, type);
      @NonNls String baseName = (suggestions.names != null && suggestions.names.length > 0) ? suggestions.names[0] : "entry";
      if (baseName == null || baseName.isEmpty()) {
        baseName = "entry";
      }
      return codeStyleManager.suggestUniqueVariableName(baseName, scope, true);
    }

    private static class ParameterAccessCollector extends JavaRecursiveElementWalkingVisitor {
      private final PsiParameter parameter;
      private final PsiElement map;
      private final String parameterName;

      private final List<PsiExpression> parameterAccesses = new ArrayList<>();

      ParameterAccessCollector(PsiParameter parameter, PsiElement map) {
        this.parameter = parameter;
        parameterName = parameter.getName();
        this.map = map;
      }

      @Override
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);
        if (expression.getQualifierExpression() != null) {
          return;
        }
        final String expressionText = expression.getText();
        if (!expressionText.equals(parameterName)) {
          return;
        }
        final PsiElement target = expression.resolve();
        if (!parameter.equals(target)) {
          return;
        }
        if (!collectValueUsage(expression)) {
          parameterAccesses.add(expression);
        }
      }

      private boolean collectValueUsage(PsiReferenceExpression expression) {
        final PsiElement parent = expression.getParent();
        if (!(parent instanceof PsiExpressionList)) {
          return false;
        }
        final PsiElement grandParent = parent.getParent();
        if (!(grandParent instanceof PsiMethodCallExpression)) {
          return false;
        }
        final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)grandParent;
        final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
        @NonNls final String methodName = methodExpression.getReferenceName();
        if (!"get".equals(methodName)) {
          return false;
        }
        final PsiExpression qualifier = methodExpression.getQualifierExpression();
        if (!(qualifier instanceof PsiReferenceExpression)) {
          return false;
        }
        final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)qualifier;
        final PsiElement target2 = referenceExpression.resolve();
        if (!map.equals(target2)) {
          return false;
        }
        final PsiExpression qualifierExpression = referenceExpression.getQualifierExpression();
        if (qualifierExpression != null &&
            !(qualifier instanceof PsiThisExpression) || qualifierExpression instanceof PsiSuperExpression) {
          return false;
        }
        parameterAccesses.add(methodCallExpression);
        return true;
      }

      List<PsiExpression> getParameterAccesses() {
        Collections.reverse(parameterAccesses);
        return parameterAccesses;
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new KeySetIterationMayUseEntrySetVisitor();
  }

  private static class KeySetIterationMayUseEntrySetVisitor extends BaseInspectionVisitor {

    @Override
    public void visitForeachStatement(PsiForeachStatement statement) {
      super.visitForeachStatement(statement);
      final PsiExpression iteratedValue = ParenthesesUtils.stripParentheses(statement.getIteratedValue());
      if (iteratedValue == null) {
        return;
      }
      final PsiExpression iteratedExpression;
      if (iteratedValue instanceof PsiReferenceExpression) {
        final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)iteratedValue;
        final PsiElement target = referenceExpression.resolve();
        if (!(target instanceof PsiLocalVariable)) {
          return;
        }
        final PsiVariable variable = (PsiVariable)target;
        final PsiMethod containingMethod = PsiTreeUtil.getParentOfType(variable, PsiMethod.class);
        if (VariableAccessUtils.variableIsAssignedAtPoint(variable, containingMethod, statement)) {
          return;
        }
        iteratedExpression = variable.getInitializer();
      }
      else {
        iteratedExpression = iteratedValue;
      }
      final PsiParameter parameter = statement.getIterationParameter();
      if (!isMapKeySetIteration(iteratedExpression, parameter, statement.getBody())) {
        return;
      }
      registerError(iteratedValue);
    }

    private static boolean isMapKeySetIteration(PsiExpression iteratedExpression, PsiVariable key, @Nullable PsiElement context) {
      if (context == null) {
        return false;
      }
      if (!(iteratedExpression instanceof PsiMethodCallExpression)) {
        return false;
      }
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)iteratedExpression;
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      @NonNls final String methodName = methodExpression.getReferenceName();
      if (!"keySet".equals(methodName)) {
        return false;
      }
      final PsiExpression expression = methodExpression.getQualifierExpression();
      if (!(expression instanceof PsiReferenceExpression)) {
        return false;
      }
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)expression;
      if (!TypeUtils.expressionHasTypeOrSubtype(referenceExpression, CommonClassNames.JAVA_UTIL_MAP)) {
        return false;
      }
      final GetValueFromMapChecker checker = new GetValueFromMapChecker(referenceExpression, key);
      context.accept(checker);
      return checker.isGetValueFromMap();
    }
  }

  private static class GetValueFromMapChecker extends JavaRecursiveElementWalkingVisitor {

    private final PsiVariable key;
    private final PsiReferenceExpression mapReference;
    private boolean getValueFromMap;
    private boolean tainted;

    GetValueFromMapChecker(@NotNull PsiReferenceExpression mapReference, @NotNull PsiVariable key) {
      this.mapReference = mapReference;
      this.key = key;
    }

    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
      if (tainted) {
        return;
      }
      super.visitReferenceExpression(expression);
      final PsiElement parent = expression.getParent();
      if (parent instanceof PsiAssignmentExpression) {
        final PsiElement target = expression.resolve();
        if (key.equals(target) || EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(mapReference, expression)) {
          tainted = true;
        }
      }
      else if (!(parent instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiMethodCallExpression)) {
        return;
      }
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)grandParent;
      final PsiReferenceExpression methodExpression = (PsiReferenceExpression)parent;
      if (!EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(mapReference, expression)) {
        return;
      }
      @NonNls final String methodName = methodExpression.getReferenceName();
      if (!"get".equals(methodName)) {
        return;
      }
      final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 1) {
        return;
      }
      final PsiExpression argument = ParenthesesUtils.stripParentheses(arguments[0]);
      if (!(argument instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)argument;
      final PsiElement argumentTarget = referenceExpression.resolve();
      if (!key.equals(argumentTarget)) {
        return;
      }
      getValueFromMap = true;
    }

    boolean isGetValueFromMap() {
      return getValueFromMap && !tainted;
    }
  }
}
