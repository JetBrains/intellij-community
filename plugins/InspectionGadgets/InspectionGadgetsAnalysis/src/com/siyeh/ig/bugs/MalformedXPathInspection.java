/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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
import com.intellij.psi.util.ConstantExpressionUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.util.HashSet;
import java.util.Set;

public class MalformedXPathInspection extends BaseInspection {

  /**
   * @noinspection StaticCollection
   */
  @NonNls
  private static final Set<String> xpathMethodNames = new HashSet<>(2);

  static {
    xpathMethodNames.add("compile");
    xpathMethodNames.add("evaluate");
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("malformed.xpath.expression.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("malformed.xpath.expression.problem.description");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MalformedXPathVisitor();
  }

  private static class MalformedXPathVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length == 0) {
        return;
      }
      final PsiExpression xpathArgument = arguments[0];
      if (!ExpressionUtils.hasStringType(xpathArgument)) {
        return;
      }
      if (!PsiUtil.isConstantExpression(xpathArgument)) {
        return;
      }
      final PsiType type = xpathArgument.getType();
      if (type == null) {
        return;
      }
      final String value = (String)ConstantExpressionUtil.computeCastTo(xpathArgument, type);
      if (value == null) {
        return;
      }
      if (!callTakesXPathExpression(expression)) {
        return;
      }
      final XPathFactory xpathFactory = XPathFactory.newInstance();
      final XPath xpath = xpathFactory.newXPath();
      //noinspection UnusedCatchParameter,ProhibitedExceptionCaught
      try {
        xpath.compile(value);
      }
      catch (XPathExpressionException ignore) {
        registerError(xpathArgument);
      }
    }

    private static boolean callTakesXPathExpression(PsiMethodCallExpression expression) {
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      final String name = methodExpression.getReferenceName();
      if (!xpathMethodNames.contains(name)) {
        return false;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return false;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return false;
      }
      final String className = containingClass.getQualifiedName();
      return "javax.xml.xpath.XPath".equals(className);
    }
  }
}