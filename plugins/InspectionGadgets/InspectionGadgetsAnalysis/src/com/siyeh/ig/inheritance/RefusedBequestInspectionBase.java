/*
 * Copyright 2003-2017 Dave Griffith, Bas Leijdekkers
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
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.CloneUtils;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.ig.ui.ExternalizableStringSet;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RefusedBequestInspectionBase extends BaseInspection {

  @SuppressWarnings("PublicField") public boolean ignoreEmptySuperMethods;

  @SuppressWarnings("PublicField") final ExternalizableStringSet annotations =
    new ExternalizableStringSet("javax.annotation.OverridingMethodsMustInvokeSuper");

  @SuppressWarnings("PublicField") boolean onlyReportWhenAnnotated = true;

  @Override
  @NotNull
  public String getID() {
    return "MethodDoesntCallSuperMethod";
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    super.writeSettings(node);
    if (onlyReportWhenAnnotated) {
      node.addContent(new Element("option").setAttribute("name", "onlyReportWhenAnnotated").
        setAttribute("value", String.valueOf(onlyReportWhenAnnotated)));
    }
    if (!annotations.hasDefaultValues()) {
      final Element element = new Element("option").setAttribute("name", "annotations");
      final Element valueElement = new Element("value");
      annotations.writeExternal(valueElement);
      node.addContent(element.addContent(valueElement));
    }
  }

  @Override
  public void readSettings(@NotNull Element node) throws InvalidDataException {
    super.readSettings(node);
    onlyReportWhenAnnotated = false;
    for (Element option : node.getChildren("option")) {
      if ("onlyReportWhenAnnotated".equals(option.getAttributeValue("name"))) {
        onlyReportWhenAnnotated = Boolean.parseBoolean(option.getAttributeValue("value"));
      }
      else if ("annotations".equals(option.getAttributeValue("name"))) {
        final Element value = option.getChild("value");
        if (value != null) {
          annotations.readExternal(value);
        }
      }
    }
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
      final PsiMethod leastConcreteSuperMethod = getDirectSuperMethod(method);
      if (leastConcreteSuperMethod == null) {
        return;
      }
      final String methodName = method.getName();
      if (!HardcodedMethodConstants.CLONE.equals(methodName)) {
        final PsiClass superClass = leastConcreteSuperMethod.getContainingClass();
        if (superClass != null) {
          final String superClassName = superClass.getQualifiedName();
          if (CommonClassNames.JAVA_LANG_OBJECT.equals(superClassName)) {
            return;
          }
        }
      }
      if (ignoreEmptySuperMethods) {
        final PsiElement element = leastConcreteSuperMethod.getNavigationElement();
        final PsiMethod superMethod = element instanceof PsiMethod ? (PsiMethod)element : leastConcreteSuperMethod;
        if (MethodUtils.isTrivial(superMethod, true)) {
          return;
        }
      }
      if (onlyReportWhenAnnotated && !CloneUtils.isClone(method) && !isJUnitSetUpOrTearDown(method) && !MethodUtils.isFinalize(method)) {
        if (!AnnotationUtil.isAnnotated(leastConcreteSuperMethod, annotations)) {
          return;
        }
      }
      if (MethodCallUtils.containsSuperMethodCall(method) || ControlFlowUtils.methodAlwaysThrowsException(method)) {
        return;
      }
      registerMethodError(method);
    }

    private boolean isJUnitSetUpOrTearDown(PsiMethod method) {
      final String name = method.getName();
      if (!"setUp".equals(name) && !"tearDown".equals(name)) {
        return false;
      }
      if (method.getParameterList().getParametersCount() != 0) {
        return false;
      }
      final PsiClass aClass = method.getContainingClass();
      return InheritanceUtil.isInheritor(aClass, "junit.framework.TestCase");
    }

    @Nullable
    private PsiMethod getDirectSuperMethod(PsiMethod method) {
      final PsiMethod superMethod = MethodUtils.getSuper(method);
      if (superMethod ==  null || superMethod.hasModifierProperty(PsiModifier.ABSTRACT)) {
        return null;
      }
      final PsiClass containingClass = superMethod.getContainingClass();
      if (containingClass == null || containingClass.isInterface()) {
        return null;
      }
      return superMethod;
    }
  }
}
