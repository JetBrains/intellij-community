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
package org.intellij.plugins.intelliLang.util;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.dataFlow.DfaPsiUtil;
import com.intellij.codeInspection.dataFlow.DfaUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.plugins.intelliLang.Configuration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Computes the constant value of an expression while considering the substitution annotation for non-compile-time
 * constant expressions.
 * <p/>
 * This is a quite simplified implementation at the moment.
 */
public class SubstitutedExpressionEvaluationHelper {

  private final PsiConstantEvaluationHelper myHelper;
  private final Configuration myConfiguration;

  public SubstitutedExpressionEvaluationHelper(Project project) {
    myHelper = JavaPsiFacade.getInstance(project).getConstantEvaluationHelper();
    myConfiguration = Configuration.getInstance();
  }

  Object computeExpression(@NotNull PsiExpression e, @NotNull List<? super PsiExpression> uncomputables) {
    return computeExpression(
      e, myConfiguration.getAdvancedConfiguration().getDfaOption(),
      myConfiguration.getAdvancedConfiguration().isIncludeUncomputablesAsLiterals(), uncomputables);
  }

  public Object computeExpression(@NotNull PsiExpression e,
                                  @NotNull Configuration.DfaOption dfaOption,
                                  boolean includeUncomputablesAsLiterals,
                                  @NotNull List<? super PsiExpression> uncomputables) {
    ConcurrentMap<PsiElement, Object> map = new ConcurrentHashMap<>();
    return myHelper.computeExpression(e, false, new PsiConstantEvaluationHelper.AuxEvaluator() {
      @Override
      public @Nullable Object computeExpression(@NotNull PsiExpression o, @NotNull PsiConstantEvaluationHelper.AuxEvaluator auxEvaluator) {
        PsiType resolvedType = null;
        if (o instanceof PsiMethodCallExpression c) {
          PsiMethod m = (PsiMethod)c.getMethodExpression().resolve();
          PsiType returnType = m != null ? m.getReturnType() : null;
          if (returnType != null && !PsiTypes.voidType().equals(returnType)) {
            // find substitution
            Object substituted = calcSubstituted(m);
            if (substituted != null) return substituted;
          }
          resolvedType = returnType;
        }
        else if (o instanceof PsiReferenceExpression) {
          PsiElement resolved = ((PsiReferenceExpression)o).resolve();
          if (resolved instanceof PsiModifierListOwner) {
            // find substitution
            Object substituted = calcSubstituted((PsiModifierListOwner)resolved);
            if (substituted != null) return substituted;
            if (resolved instanceof PsiVariable psiVariable) {
              resolvedType = psiVariable.getType();
              Collection<PsiExpression> values;
              if (dfaOption == Configuration.DfaOption.ASSIGNMENTS) {
                values = DfaPsiUtil.getVariableAssignmentsInFile(psiVariable, true, o);
              }
              else if (dfaOption == Configuration.DfaOption.DFA) {
                values = DfaUtil.getVariableValues(psiVariable, o);
              }
              else if (dfaOption == Configuration.DfaOption.RESOLVE) {
                PsiExpression initializer = psiVariable.getInitializer();
                values = ContainerUtil.createMaybeSingletonList(initializer);
              }
              else {
                values = Collections.emptyList();
              }
              // return the first computed value as far as we do not support multiple injection
              for (PsiExpression value : values) {
                Object computedValue = auxEvaluator.computeExpression(value, this);
                if (computedValue != null) {
                  return computedValue;
                }
              }
            }
          }
        }
        uncomputables.add(o);
        if (includeUncomputablesAsLiterals) {
          if (resolvedType != null) {
            if (PsiTypes.doubleType().isAssignableFrom(resolvedType)) return 1; // magic number!
          }
          StringBuilder sb = new StringBuilder();
          o.accept(new PsiRecursiveElementWalkingVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
              if (element instanceof PsiExpressionList) return;
              if (element instanceof PsiIdentifier) {
                if (sb.length() > 0) sb.append(".");
                sb.append(element.getText());
              }
              super.visitElement(element);
            }
          });
          return sb.toString();
        }
        return null;
      }

      @Override
      public @NotNull ConcurrentMap<PsiElement, Object> getCacheMap(boolean overflow) {
        return map;
      }
    });
  }

  private @Nullable Object calcSubstituted(PsiModifierListOwner owner) {
    Set<String> substAnnos = myConfiguration.getAdvancedConfiguration().getSubstAnnotationPair().second;
    PsiAnnotation annotation = AnnotationUtil.findAnnotation(owner, substAnnos);
    return annotation != null ? AnnotationUtilEx.calcAnnotationValue(annotation, "value") : null;
  }
}
