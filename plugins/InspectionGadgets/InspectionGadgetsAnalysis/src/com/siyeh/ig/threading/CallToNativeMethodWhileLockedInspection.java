/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.threading;

import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.SynchronizationUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class CallToNativeMethodWhileLockedInspection extends BaseInspection {
  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "call.to.native.method.while.locked.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "call.to.native.method.while.locked.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new CallToNativeMethodWhileLockedVisitor();
  }

  private static class CallToNativeMethodWhileLockedVisitor extends BaseInspectionVisitor {

    private static final Set<String> EXCLUDED_CLASS_NAMES =
      ContainerUtil.immutableSet(CommonClassNames.JAVA_LANG_OBJECT, "java.lang.System", "sun.misc.Unsafe", "java.lang.invoke.MethodHandle");

    @Override
    public void visitMethodCallExpression(
      @NotNull PsiMethodCallExpression expression) {
      final PsiMethod method = expression.resolveMethod();
      if (method == null || !method.hasModifierProperty(PsiModifier.NATIVE)) return;

      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) return;
      if (EXCLUDED_CLASS_NAMES.contains(containingClass.getQualifiedName())) return;

      if (!SynchronizationUtil.isInSynchronizedContext(expression)) return;
      registerMethodCallError(expression);
    }
  }
}