// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.unused.defaultParameter;

import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.FindSuperElementsHelper;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.GroovyLocalInspectionTool;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

public class GrUnusedDefaultParameterInspection extends GroovyLocalInspectionTool implements CleanupLocalInspectionTool {

  @Override
  public @NotNull GroovyElementVisitor buildGroovyVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new GroovyElementVisitor() {
      @Override
      public void visitExpression(@NotNull GrExpression expression) {
        PsiElement expressionParent = expression.getParent();
        if (!(expressionParent instanceof GrParameter)) return;

        GrParameter parameter = (GrParameter)expressionParent;
        if (parameter.getInitializerGroovy() != expression) return;

        PsiElement parameterParent = parameter.getParent();
        if (!(parameterParent instanceof GrParameterList)) return;

        PsiElement parameterListParent = parameterParent.getParent();
        if (!(parameterListParent instanceof GrMethod)) return;

        GrMethod method = (GrMethod)parameterListParent;
        if (PsiUtil.OPERATOR_METHOD_NAMES.contains(method.getName())) return;

        if (isInitializerUnused(parameter, method)) {
          holder.registerProblem(
            expression, GroovyBundle.message("unused.default.parameter.message"), ProblemHighlightType.LIKE_UNUSED_SYMBOL,
            QuickFixFactory.getInstance().createDeleteFix(expression, GroovyBundle.message("unused.default.parameter.fix"))
          );
        }
      }
    };
  }

  /**
   * Consider following method:
   * <pre>
   *   def foo(a = 1, b = 2, c = 3) {}
   * </pre>
   * Its reflected methods:
   * <pre>
   *   def foo(a, b, c) {}
   *   def foo(a, b) {}
   *   def foo(a) {}
   *   def foo() {}
   * </pre>
   * Initializer for '{@code a}' is used only when {@code foo} called without arguments,
   * we do not care if {@code foo} is called with one, two ot three arguments.
   * <p>
   * In case of {@code b} we search {@code foo()} or {@code foo(1)} calls.
   * <p>
   * The general idea: search usages of last N reflected methods where N is number of current parameter among other default parameters.
   */
  private static boolean isInitializerUnused(@NotNull GrParameter parameter, @NotNull GrMethod method) {
    int optionalParameterNumber = 0;
    for (GrParameter someParameter : method.getParameters()) {
      if (someParameter.isOptional()) optionalParameterNumber++;
      if (someParameter == parameter) break;
    }

    GrReflectedMethod[] reflectedMethods = method.getReflectedMethods();
    for (int i = reflectedMethods.length - optionalParameterNumber; i < reflectedMethods.length; i++) {
      GrReflectedMethod reflectedMethod = reflectedMethods[i];
      if (FindSuperElementsHelper.findSuperElements(reflectedMethod).length > 0) return false;
      if (MethodReferencesSearch.search(reflectedMethod).findFirst() != null) return false;
    }
    return true;
  }
}
