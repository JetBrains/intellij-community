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
package com.siyeh.ig.performance;

import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.psi.impl.AllowedApiFilterExtension;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.HashSet;

public class DynamicRegexReplaceableByCompiledPatternInspectionBase extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean ignoreForSplitOptimization = true;

  @NonNls
  protected static final Collection<String> regexMethodNames = new HashSet<>(4);
  static {
    regexMethodNames.add("matches");
    regexMethodNames.add("replace");
    regexMethodNames.add("replaceFirst");
    regexMethodNames.add("replaceAll");
    regexMethodNames.add("split");
  }

  @Override
  @Nls
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "dynamic.regex.replaceable.by.compiled.pattern.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "dynamic.regex.replaceable.by.compiled.pattern.problem.descriptor");
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel("Ignore for String.split() optimizations (since Java 7)", this, "ignoreForSplitOptimization");
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    if (!ignoreForSplitOptimization) {
      node.addContent(new Element("option").setAttribute("name", "ignoreForSplitOptimization").setAttribute("value", "false"));
    }
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new DynamicRegexReplaceableByCompiledPatternVisitor();
  }

  private class DynamicRegexReplaceableByCompiledPatternVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (!isCallToRegexMethod(expression)) {
        return;
      }
      registerMethodCallError(expression);
    }

    private boolean isCallToRegexMethod(PsiMethodCallExpression expression) {
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      final String name = methodExpression.getReferenceName();
      if (!regexMethodNames.contains(name)) {
        return false;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length == 0) {
        return false;
      }
      final Object value = ExpressionUtils.computeConstantExpression(arguments[0]);
      if (!(value instanceof String)) {
        return false;
      }
      final String regex = (String)value;
      if (ignoreForSplitOptimization && "split".equals(name) && isOptimizedPattern(regex)) {
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
      if (!CommonClassNames.JAVA_LANG_STRING.equals(className)) {
        return false;
      }
      if (Extensions.getRootArea().hasExtensionPoint(AllowedApiFilterExtension.EP_NAME.getName())) {
        //todo[nik] remove this condition when the extension point will be registered in java-analysis-impl module
        return AllowedApiFilterExtension.isClassAllowed("java.util.regex.Pattern", expression);
      }
      return true;
    }

    private boolean isOptimizedPattern(String regex) {
      // from String.split()
      int ch;
      return ((regex.length() == 1 &&
               ".$|()[{^?*+\\".indexOf(ch = regex.charAt(0)) == -1) ||
              (regex.length() == 2 &&
               regex.charAt(0) == '\\' &&
               (((ch = regex.charAt(1))-'0')|('9'-ch)) < 0 &&
               ((ch-'a')|('z'-ch)) < 0 &&
               ((ch-'A')|('Z'-ch)) < 0)) &&
             (ch < Character.MIN_HIGH_SURROGATE ||
              ch > Character.MAX_LOW_SURROGATE);
    }
  }
}
