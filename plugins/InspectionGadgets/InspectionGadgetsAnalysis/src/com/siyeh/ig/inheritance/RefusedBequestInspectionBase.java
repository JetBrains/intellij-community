/*
 * Copyright 2003-2014 Dave Griffith, Bas Leijdekkers
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
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.ui.ExternalizableStringSet;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RefusedBequestInspectionBase extends BaseInspection {

  @SuppressWarnings("PublicField") public boolean ignoreEmptySuperMethods = false;

  @SuppressWarnings("PublicField") final ExternalizableStringSet annotations =
    new ExternalizableStringSet("javax.annotation.OverridingMethodsMustInvokeSuper");

  @SuppressWarnings("PublicField") boolean onlyReportWhenAnnotated = false;

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
      final PsiMethod leastConcreteSuperMethod = getLeastConcreteSuperMethod(method);
      if (leastConcreteSuperMethod == null) {
        return;
      }
      final PsiClass objectClass = ClassUtils.findObjectClass(method);
      final PsiMethod[] superMethods = method.findSuperMethods(objectClass);
      if (superMethods.length > 0) {
        return;
      }
      if (ignoreEmptySuperMethods) {
        final PsiMethod superMethod = (PsiMethod)leastConcreteSuperMethod.getNavigationElement();
        if (isTrivial(superMethod)) {
          return;
        }
      }
      if (onlyReportWhenAnnotated) {
        if (!AnnotationUtil.isAnnotated(leastConcreteSuperMethod, annotations)) {
          return;
        }
      }
      if (containsSuperCall(body, leastConcreteSuperMethod)) {
        return;
      }
      registerMethodError(method);
    }

    private boolean isTrivial(PsiMethod method) {
      final PsiCodeBlock body = method.getBody();
      if (body == null) {
        return true;
      }
      final PsiStatement[] statements = body.getStatements();
      if (statements.length == 0) {
        return true;
      }
      if (statements.length > 1) {
        return false;
      }
      final PsiStatement statement = statements[0];
      if (statement instanceof PsiThrowStatement) {
        return true;
      }
      if (statement instanceof PsiReturnStatement) {
        final PsiReturnStatement returnStatement = (PsiReturnStatement)statement;
        final PsiExpression returnValue = returnStatement.getReturnValue();
        if (returnValue instanceof PsiLiteralExpression) {
          return true;
        }
      }
      return false;
    }

    @Nullable
    private PsiMethod getLeastConcreteSuperMethod(PsiMethod method) {
      final PsiMethod[] superMethods = method.findSuperMethods(true);
      for (final PsiMethod superMethod : superMethods) {
        final PsiClass containingClass = superMethod.getContainingClass();
        if (containingClass != null && !superMethod.hasModifierProperty(PsiModifier.ABSTRACT) && !containingClass.isInterface()) {
          return superMethod;
        }
      }
      return null;
    }

    private boolean containsSuperCall(@NotNull PsiElement context, @NotNull PsiMethod method) {
      final SuperCallVisitor visitor = new SuperCallVisitor(method);
      context.accept(visitor);
      return visitor.hasSuperCall();
    }
  }

  private static class SuperCallVisitor extends JavaRecursiveElementVisitor {

    private final PsiMethod methodToSearchFor;
    private boolean hasSuperCall = false;

    SuperCallVisitor(PsiMethod methodToSearchFor) {
      this.methodToSearchFor = methodToSearchFor;
    }

    @Override
    public void visitElement(@NotNull PsiElement element) {
      if (hasSuperCall) {
        return;
      }
      super.visitElement(element);
    }

    @Override
    public void visitMethodCallExpression(
      @NotNull PsiMethodCallExpression expression) {
      if (hasSuperCall) {
        return;
      }
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (qualifier == null) {
        return;
      }
      final String text = qualifier.getText();
      if (!PsiKeyword.SUPER.equals(text)) {
        return;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      if (method.equals(methodToSearchFor)) {
        hasSuperCall = true;
      }
    }

    public boolean hasSuperCall() {
      return hasSuperCall;
    }
  }
}
