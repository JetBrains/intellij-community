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

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.OrderedSet;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.IgnoreClassFix;
import com.siyeh.ig.fixes.SuppressForTestsScopeFix;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.ui.UiUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public class ResultOfObjectAllocationIgnoredInspection extends BaseInspection {

  @SuppressWarnings("PublicField") public OrderedSet<String> ignoredClasses = new OrderedSet<>();

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return UiUtils.createTreeClassChooserList(ignoredClasses, "Ignored classes", "Choose class for which object allocation can be ignored");
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final PsiElement context = (PsiElement)infos[0];
    return SuppressForTestsScopeFix.build(this, context);
  }

  @NotNull
  @Override
  protected InspectionGadgetsFix[] buildFixes(Object... infos) {
    final List<InspectionGadgetsFix> result = new SmartList<>();
    final PsiExpression expression = (PsiExpression)infos[0];
    final PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(expression.getType());
    if (aClass != null) {
      final String name = aClass.getQualifiedName();
      if (name != null) {
        result.add(new IgnoreClassFix(name, ignoredClasses, "Ignore allocations of objects with type '" + name + "'"));
      }
    }
    ContainerUtil.addIfNotNull(result, SuppressForTestsScopeFix.build(this, expression));
    return result.toArray(InspectionGadgetsFix.EMPTY_ARRAY);
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("result.of.object.allocation.ignored.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("result.of.object.allocation.ignored.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ResultOfObjectAllocationIgnoredVisitor();
  }

  private class ResultOfObjectAllocationIgnoredVisitor extends BaseInspectionVisitor {

    @Override
    public void visitExpressionStatement(@NotNull PsiExpressionStatement statement) {
      super.visitExpressionStatement(statement);
      final PsiExpression expression = statement.getExpression();
      if (!(expression instanceof PsiNewExpression)) {
        return;
      }
      final PsiNewExpression newExpression = (PsiNewExpression)expression;
      if (ExpressionUtils.isArrayCreationExpression(newExpression)) {
        return;
      }
      final PsiJavaCodeReferenceElement reference = newExpression.getClassOrAnonymousClassReference();
      if (reference == null) {
        return;
      }
      final PsiElement target = reference.resolve();
      if (!(target instanceof PsiClass)) {
        return;
      }
      final PsiClass aClass = (PsiClass)target;
      if (!(expression instanceof PsiAnonymousClass) && ignoredClasses.contains(aClass.getQualifiedName())) {
        return;
      }
      registerNewExpressionError(newExpression, newExpression);
    }
  }
}
