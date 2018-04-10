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
package com.siyeh.ig.inheritance;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.*;
import com.siyeh.ig.ui.ExternalizableStringSet;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RefusedBequestInspectionBase extends BaseInspection {

  @SuppressWarnings("PublicField") public boolean ignoreEmptySuperMethods;

  @SuppressWarnings("PublicField") public final ExternalizableStringSet annotations =
    new ExternalizableStringSet("javax.annotation.OverridingMethodsMustInvokeSuper");

  @SuppressWarnings("PublicField") public boolean onlyReportWhenAnnotated = true;

  @Override
  @NotNull
  public String getID() {
    return "MethodDoesntCallSuperMethod";
  }

  @Override
  public void writeSettings(@NotNull Element node) {
    defaultWriteSettings(node, "onlyReportWhenAnnotated", "annotations");
    writeBooleanOption(node, "onlyReportWhenAnnotated", false);
    annotations.writeSettings(node, "annotations");
  }

  @Override
  public void readSettings(@NotNull Element node) {
    onlyReportWhenAnnotated = false;
    super.readSettings(node);
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("refused.bequest.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("refused.bequest.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new RefusedBequestVisitor();
  }

  private class RefusedBequestVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      super.visitMethod(method);
      final PsiCodeBlock body = method.getBody();
      if (body == null) {
        return;
      }
      if (method.getNameIdentifier() == null) {
        return;
      }
      final PsiMethod superMethod = getDirectSuperMethod(method);
      if (superMethod == null) {
        return;
      }
      final String methodName = method.getName();
      if (!HardcodedMethodConstants.CLONE.equals(methodName)) {
        final PsiClass superClass = superMethod.getContainingClass();
        if (superClass != null) {
          final String superClassName = superClass.getQualifiedName();
          if (CommonClassNames.JAVA_LANG_OBJECT.equals(superClassName)) {
            return;
          }
        }
      }
      if (ignoreEmptySuperMethods && isTrivial(superMethod)) {
        return;
      }
      final boolean isClone = CloneUtils.isClone(method);
      if (onlyReportWhenAnnotated && !AnnotationUtil.isAnnotated(superMethod, annotations, 0)) {
        if (!isClone && !isJUnitSetUpOrTearDown(method) && !MethodUtils.isFinalize(method) || isTrivial(superMethod)) {
          return;
        }
      }
      if (isClone && ClassUtils.isSingleton(method.getContainingClass())) {
        return;
      }
      if (MethodCallUtils.containsSuperMethodCall(method) || ControlFlowUtils.methodAlwaysThrowsException(method)) {
        return;
      }
      registerMethodError(method);
    }

    private boolean isTrivial(PsiMethod method) {
      final PsiElement element = method.getNavigationElement();
      return MethodUtils.isTrivial(element instanceof PsiMethod ? (PsiMethod)element : method, true);
    }

    private boolean isJUnitSetUpOrTearDown(PsiMethod method) {
      final String name = method.getName();
      if (!"setUp".equals(name) && !"tearDown".equals(name)) {
        return false;
      }
      if (!method.getParameterList().isEmpty()) {
        return false;
      }
      final PsiClass aClass = method.getContainingClass();
      return InheritanceUtil.isInheritor(aClass, "junit.framework.TestCase");
    }

    @Nullable
    private PsiMethod getDirectSuperMethod(PsiMethod method) {
      final PsiMethod superMethod = MethodUtils.getSuper(method);
      return superMethod == null || superMethod.hasModifierProperty(PsiModifier.ABSTRACT) ? null : superMethod;
    }
  }
}
