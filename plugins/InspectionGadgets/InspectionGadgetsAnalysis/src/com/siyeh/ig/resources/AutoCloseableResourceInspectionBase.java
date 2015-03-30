/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.siyeh.ig.resources;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public class AutoCloseableResourceInspectionBase extends BaseInspection {

  public static final List<String> DEFAULT_IGNORED_TYPES =
    Arrays.asList("java.util.stream.Stream", "java.util.stream.IntStream", "java.util.stream.LongStream", "java.util.stream.DoubleStream");
  @SuppressWarnings("PublicField")
  public boolean ignoreFromMethodCall = false;

  final List<String> ignoredTypes = new ArrayList<String>(DEFAULT_IGNORED_TYPES);

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("auto.closeable.resource.display.name");
  }

  @NotNull
  @Override
  public String getID() {
    return "resource"; // matches Eclipse inspection
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    final PsiExpression expression = (PsiExpression)infos[0];
    final PsiType type = expression.getType();
    assert type != null;
    final String text = type.getPresentableText();
    return InspectionGadgetsBundle.message("auto.closeable.resource.problem.descriptor", text);
  }

  @Override
  public void readSettings(@NotNull Element node) throws InvalidDataException {
    super.readSettings(node);
    for (Element option : node.getChildren("option")) {
      final String name = option.getAttributeValue("name");
      if ("ignoredTypes".equals(name)) {
        final String ignoredTypesString = option.getAttributeValue("value");
        if (ignoredTypesString != null) {
          ignoredTypes.clear();
          parseString(ignoredTypesString, ignoredTypes);
        }
      }
    }
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    super.writeSettings(node);
    if (!DEFAULT_IGNORED_TYPES.equals(ignoredTypes)) {
      final String ignoredTypesString = formatString(ignoredTypes);
      node.addContent(new Element("option").setAttribute("name", "ignoredTypes").setAttribute("value", ignoredTypesString));
    }
  }

  @Override
  public boolean shouldInspect(PsiFile file) {
    return PsiUtil.isLanguageLevel7OrHigher(file);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new AutoCloseableResourceVisitor();
  }

  private class AutoCloseableResourceVisitor extends BaseInspectionVisitor {

    @Override
    public void visitNewExpression(PsiNewExpression expression) {
      super.visitNewExpression(expression);
      if (!isNotSafelyClosedResource(expression)) {
        return;
      }
      registerNewExpressionError(expression, expression);
    }

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (ignoreFromMethodCall) {
        return;
      }
      if (!isNotSafelyClosedResource(expression)) {
        return;
      }
      registerMethodCallError(expression, expression);
    }

    private boolean isNotSafelyClosedResource(PsiExpression expression) {
      if (!TypeUtils.expressionHasTypeOrSubtype(expression, "java.lang.AutoCloseable")) {
        return false;
      }
      if (TypeUtils.expressionHasTypeOrSubtype(expression, ignoredTypes)) {
        return false;
      }
      final PsiVariable variable = ResourceInspection.getVariable(expression);
      return !(variable instanceof PsiResourceVariable) && !ResourceInspection.isResourceEscapingFromMethod(variable, expression);
    }
  }
}
