/*
 * Copyright 2003-2018 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.PsiElementProcessorAdapter;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.EqualityToEqualsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public class ObjectEqualityInspection extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public boolean m_ignoreEnums = true;

  /**
   * @noinspection PublicField
   */
  public boolean m_ignoreClassObjects = true;

  /**
   * @noinspection PublicField
   */
  public boolean m_ignorePrivateConstructors = false;

  @Override
  public @NotNull String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("object.comparison.problem.description");
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("m_ignoreEnums", InspectionGadgetsBundle.message("object.comparison.enumerated.ignore.option")),
      checkbox("m_ignoreClassObjects", InspectionGadgetsBundle.message("object.comparison.klass.ignore.option")),
      checkbox("m_ignorePrivateConstructors", InspectionGadgetsBundle.message(
        "object.equality.ignore.between.objects.of.a.type.with.only.private.constructors.option")));
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ObjectEqualityVisitor();
  }

  @Override
  public InspectionGadgetsFix @NotNull [] buildFixes(Object... infos) {
    return EqualityToEqualsFix.buildEqualityFixes((PsiBinaryExpression)infos[0]);
  }

  private class ObjectEqualityVisitor extends BaseInspectionVisitor {

    @Override
    public void visitBinaryExpression(@NotNull PsiBinaryExpression expression) {
      super.visitBinaryExpression(expression);
      if (!ComparisonUtils.isEqualityComparison(expression)) {
        return;
      }
      final PsiExpression rhs = expression.getROperand();
      if (!isObjectType(rhs)) {
        return;
      }
      final PsiExpression lhs = expression.getLOperand();
      if (!isObjectType(lhs)) {
        return;
      }
      final PsiMethod method = PsiTreeUtil.getParentOfType(expression, PsiMethod.class);
      if (MethodUtils.isEquals(method) &&
          (isThisReference(lhs, method.getContainingClass()) || isThisReference(rhs, method.getContainingClass()))) {
        return;
      }
      if (m_ignoreEnums && (TypeConversionUtil.isEnumType(lhs.getType()) || TypeConversionUtil.isEnumType(rhs.getType()))) {
        return;
      }
      ProblemHighlightType highlightType;
      if (shouldHighlight(expression, rhs, lhs)) {
        highlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
      }
      else {
        if (!isOnTheFly()) {
          return;
        }
        highlightType = ProblemHighlightType.INFORMATION;
      }
      registerError(expression.getOperationSign(), highlightType, expression);
    }

    private boolean shouldHighlight(@NotNull PsiBinaryExpression expression,
                                    PsiExpression rhs,
                                    PsiExpression lhs) {
      if (!TypeConversionUtil.isBinaryOperatorApplicable(expression.getOperationTokenType(), lhs, rhs, false)) {
        // don't warn on non-compiling code, but allow to replace
        return false;
      }
      if (m_ignoreClassObjects && (ClassUtils.isFinalClassWithDefaultEquals(PsiUtil.resolveClassInClassTypeOnly(lhs.getType())) ||
                                   ClassUtils.isFinalClassWithDefaultEquals(PsiUtil.resolveClassInClassTypeOnly(rhs.getType())))) {
        return false;
      }
      if (m_ignorePrivateConstructors && (typeHasPrivateConstructor(lhs) || typeHasPrivateConstructor(rhs))) {
        return false;
      }
      return true;
    }

    private boolean typeHasPrivateConstructor(@Nullable PsiExpression expression) {
      if (expression == null) {
        return false;
      }
      final PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(expression.getType());
      return (aClass != null && aClass.isInterface())
             ? implementersHaveOnlyPrivateConstructors(aClass)
             : ClassUtils.hasOnlyPrivateConstructors(aClass);
    }

    private boolean implementersHaveOnlyPrivateConstructors(final PsiClass aClass) {
      final GlobalSearchScope scope = GlobalSearchScope.allScope(aClass.getProject());
      final PsiElementProcessor.CollectElementsWithLimit<PsiClass> processor = new PsiElementProcessor.CollectElementsWithLimit<>(6);
      final ProgressManager progressManager = ProgressManager.getInstance();
      progressManager.runProcess(
        (Runnable)() -> ClassInheritorsSearch.search(aClass, scope, true).forEach(new PsiElementProcessorAdapter<>(processor)), null);
      if (processor.isOverflow()) {
        return false;
      }
      final Collection<PsiClass> implementers = processor.getCollection();
      for (PsiClass implementer : implementers) {
        if (!implementer.isInterface() &&
            !implementer.hasModifierProperty(PsiModifier.ABSTRACT) &&
            !ClassUtils.hasOnlyPrivateConstructors(implementer)) {
          return false;
        }
      }
      return true;
    }

    private boolean isObjectType(PsiExpression expression) {
      if (expression == null) {
        return false;
      }
      final PsiType type = expression.getType();
      return type != null &&
             !(type instanceof PsiArrayType) &&
             !(type instanceof PsiPrimitiveType) &&
             !TypeUtils.isJavaLangString(type) &&
             !TypeUtils.expressionHasTypeOrSubtype(expression, CommonClassNames.JAVA_LANG_NUMBER);
    }

    private boolean isThisReference(@Nullable PsiExpression expression, @Nullable PsiClass psiClass) {
      if (!(expression instanceof PsiThisExpression thisExpression)) {
        return false;
      }
      final PsiJavaCodeReferenceElement qualifier = thisExpression.getQualifier();
      return qualifier == null || psiClass != null && qualifier.isReferenceTo(psiClass);
    }
  }
}
