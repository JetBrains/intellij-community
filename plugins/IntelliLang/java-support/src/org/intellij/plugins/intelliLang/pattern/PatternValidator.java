/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.plugins.intelliLang.pattern;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.siyeh.ig.fixes.IntroduceVariableFix;
import org.intellij.plugins.intelliLang.Configuration;
import org.intellij.plugins.intelliLang.IntelliLangBundle;
import org.intellij.plugins.intelliLang.util.AnnotationUtilEx;
import org.intellij.plugins.intelliLang.util.PsiUtilEx;
import org.intellij.plugins.intelliLang.util.SubstitutedExpressionEvaluationHelper;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Inspection that validates if string literals, compile-time constants or
 * substituted expressions match the pattern of the context they're used in.
 */
public class PatternValidator extends LocalInspectionTool {
  private static final Key<CachedValue<Pattern>> COMPLIED_PATTERN = Key.create("COMPILED_PATTERN");

  public boolean CHECK_NON_CONSTANT_VALUES = true;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return OptPane.pane(
      OptPane.checkbox("CHECK_NON_CONSTANT_VALUES", IntelliLangBundle.message("flag.non.compile.time.constant.expressions"))
    );
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(final @NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {

      @Override
      public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
        visitExpression(expression);
      }

      @Override
      public void visitExpression(@NotNull PsiExpression expression) {
        final PsiElement element = expression.getParent();
        if (element instanceof PsiExpressionList) {
          // this checks method arguments
          check(expression, holder, false);
        }
        else if (element instanceof PsiNameValuePair valuePair) {
          final String name = valuePair.getName();
          if (name == null || name.equals(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME)) {
            // check whether @Subst complies with pattern
            check(expression, holder, true);
          }
          check(expression, holder, false);
        }
      }

      @Override
      public void visitReturnStatement(@NotNull PsiReturnStatement statement) {
        final PsiExpression returnValue = statement.getReturnValue();
        if (returnValue != null) {
          check(returnValue, holder, false);
        }
      }

      @Override
      public void visitVariable(@NotNull PsiVariable var) {
        final PsiExpression initializer = var.getInitializer();
        if (initializer != null) {
          // variable/field initializer
          check(initializer, holder, false);
        }
      }

      @Override
      public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expression) {
        final PsiExpression e = expression.getRExpression();
        if (e != null) {
          check(e, holder, false);
        }
        visitExpression(expression);
      }

      private void check(@NotNull PsiExpression expression, ProblemsHolder holder, boolean isAnnotationValue) {
        if (expression instanceof PsiConditionalExpression expr) {
          PsiExpression e = expr.getThenExpression();
          if (e != null) {
            check(e, holder, isAnnotationValue);
          }
          e = expr.getElseExpression();
          if (e != null) {
            check(e, holder, isAnnotationValue);
          }
        }
        else {
          final PsiType type = expression.getType();
          // optimiziation: only check expressions of type String
          if (type != null && PsiUtilEx.isString(type)) {
            final PsiModifierListOwner element;
            if (isAnnotationValue) {
              final PsiAnnotation psiAnnotation = PsiTreeUtil.getParentOfType(expression, PsiAnnotation.class);
              if (psiAnnotation != null && Configuration.getInstance()
                .getAdvancedConfiguration().getSubstAnnotationClass().equals(psiAnnotation.getQualifiedName())) {
                element = PsiTreeUtil.getParentOfType(expression, PsiModifierListOwner.class);
              }
              else {
                return;
              }
            }
            else {
              element = AnnotationUtilEx.getAnnotatedElementFor(expression, AnnotationUtilEx.LookupType.PREFER_CONTEXT);
            }
            if (element != null && PsiUtilEx.isLanguageAnnotationTarget(element)) {
              PsiAnnotation[] annotations = AnnotationUtilEx.getAnnotationFrom(element,
                                                                               Configuration.getInstance().getAdvancedConfiguration()
                                                                                 .getPatternAnnotationPair(), true);
              checkExpression(expression, annotations, holder);
            }
          }
        }
      }
    };
  }

  private void checkExpression(PsiExpression expression, final PsiAnnotation[] annotations, ProblemsHolder holder) {
    if (annotations.length == 0) return;
    final PsiAnnotation psiAnnotation = annotations[0];

    // cache compiled pattern with annotation
    CachedValue<Pattern> p = psiAnnotation.getUserData(COMPLIED_PATTERN);
    if (p == null) {
      final CachedValueProvider<Pattern> provider = () -> {
        final String pattern = AnnotationUtilEx.calcAnnotationValue(psiAnnotation, "value");
        Pattern p1 = null;
        if (pattern != null) {
          try {
            p1 = Pattern.compile(pattern);
          }
          catch (PatternSyntaxException e) {
            // pattern stays null
          }
        }
        return CachedValueProvider.Result.create(p1, (Object[])annotations);
      };
      p = CachedValuesManager.getManager(expression.getProject()).createCachedValue(provider, false);
      psiAnnotation.putUserData(COMPLIED_PATTERN, p);
    }

    final Pattern pattern = p.getValue();
    if (pattern == null) return;

    List<PsiExpression> nonConstantElements = new SmartList<>();
    Configuration configuration = Configuration.getInstance();
    final Object result = new SubstitutedExpressionEvaluationHelper(expression.getProject()).computeExpression(
      expression, configuration.getAdvancedConfiguration().getDfaOption(), false, nonConstantElements);
    final String o = result == null ? null : String.valueOf(result);
    if (o != null) {
      if (!pattern.matcher(o).matches()) {
        if (annotations.length > 1) {
          // the last element contains the element's actual annotation
          final String fqn = annotations[annotations.length - 1].getQualifiedName();
          assert fqn != null;

          final String name = StringUtil.getShortName(fqn);
          holder.registerProblem(expression,
                                 IntelliLangBundle.message("inspection.message.expression.does.not.match.pattern", o, name,
                                                           pattern.pattern()));
        }
        else {
          holder.registerProblem(expression,
                                 IntelliLangBundle.message("inspection.message.expression.does.not.match.pattern2", o, pattern.pattern()));
        }
      }
    }
    else if (CHECK_NON_CONSTANT_VALUES) {
      for (PsiExpression expr : nonConstantElements) {
        final PsiElement e;
        if (expr instanceof PsiReferenceExpression ref) {
          e = ref.resolve();
        }
        else if (expr instanceof PsiMethodCallExpression call) {
          e = call.getMethodExpression().resolve();
        }
        else {
          e = expr;
        }
        final PsiModifierListOwner owner = ObjectUtils.tryCast(e, PsiModifierListOwner.class);
        if (owner != null && PsiUtilEx.isLanguageAnnotationTarget(owner)) {
          PsiAnnotation[] resolvedAnnos =
            AnnotationUtilEx.getAnnotationFrom(owner, configuration.getAdvancedConfiguration().getPatternAnnotationPair(), true);
          if (resolvedAnnos.length == 2 &&
              annotations.length == 2 &&
              Comparing.strEqual(resolvedAnnos[1].getQualifiedName(), annotations[1].getQualifiedName())) {
            // both target and source annotated indirectly with the same anno
            return;
          }
        }
        holder.registerProblem(expr, IntelliLangBundle.message("inspection.pattern.validator.description"),
                               new IntroduceVariableFix(false));
      }
    }
  }
}