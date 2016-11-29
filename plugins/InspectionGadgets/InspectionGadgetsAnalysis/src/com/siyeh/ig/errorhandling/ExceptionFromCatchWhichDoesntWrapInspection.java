/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.errorhandling;

import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Processor;
import com.intellij.util.Query;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.HashSet;
import java.util.Set;

public class ExceptionFromCatchWhichDoesntWrapInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean ignoreGetMessage;

  @SuppressWarnings("PublicField")
  public boolean ignoreCantWrap;

  @Override
  @NotNull
  public String getID() {
    return "ThrowInsideCatchBlockWhichIgnoresCaughtException";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("exception.from.catch.which.doesnt.wrap.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("exception.from.catch.which.doesnt.wrap.problem.descriptor");
  }

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox(InspectionGadgetsBundle.message("exception.from.catch.which.doesntwrap.ignore.option"), "ignoreGetMessage");
    panel.addCheckbox(InspectionGadgetsBundle.message("exception.from.catch.which.doesntwrap.ignore.cant.wrap.option"), "ignoreCantWrap");
    return panel;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ExceptionFromCatchWhichDoesntWrapVisitor();
  }

  private class ExceptionFromCatchWhichDoesntWrapVisitor extends BaseInspectionVisitor {

    @Override
    public void visitThrowStatement(PsiThrowStatement statement) {
      super.visitThrowStatement(statement);
      final PsiCatchSection catchSection = PsiTreeUtil.getParentOfType(statement, PsiCatchSection.class, true, PsiClass.class);
      if (catchSection == null) {
        return;
      }
      final PsiParameter parameter = catchSection.getParameter();
      if (parameter == null) {
        return;
      }
      @NonNls final String parameterName = parameter.getName();
      if (PsiUtil.isIgnoredName(parameterName)) {
        return;
      }
      final PsiExpression exception = statement.getException();
      if (exception == null) {
        return;
      }
      if (ignoreCantWrap) {
        final PsiType thrownType = exception.getType();
        if (thrownType instanceof PsiClassType) {
          final PsiClassType classType = (PsiClassType)thrownType;
          final PsiClass exceptionClass = classType.resolve();
          if (exceptionClass != null) {
            final PsiMethod[] constructors = exceptionClass.getConstructors();
            final PsiClassType throwableType = TypeUtils.getType(CommonClassNames.JAVA_LANG_THROWABLE, statement);
            boolean canWrap = false;
            outer:
            for (PsiMethod constructor : constructors) {
              final PsiParameterList parameterList = constructor.getParameterList();
              final PsiParameter[] parameters = parameterList.getParameters();
              for (PsiParameter constructorParameter : parameters) {
                final PsiType type = constructorParameter.getType();
                if (throwableType.equals(type)) {
                  canWrap = true;
                  break outer;
                }
              }
            }
            if (!canWrap) {
              return;
            }
          }
        }
      }
      final ReferenceFinder visitor = new ReferenceFinder(parameter);
      exception.accept(visitor);
      if (visitor.usesParameter()) {
        return;
      }
      registerStatementError(statement);
    }
  }

  private class ReferenceFinder extends JavaRecursiveElementVisitor {

    private final Set<PsiReferenceExpression> visited = new HashSet<>();
    private boolean argumentsContainCatchParameter;
    private final PsiParameter parameter;

    ReferenceFinder(PsiParameter parameter) {
      this.parameter = parameter;
    }

    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
      if (argumentsContainCatchParameter || !visited.add(expression)) {
        return;
      }
      super.visitReferenceExpression(expression);
      final PsiElement target = expression.resolve();
      if (!parameter.equals(target)) {
        if (target instanceof PsiLocalVariable) {
          final PsiLocalVariable variable = (PsiLocalVariable)target;
          final Query<PsiReference> query = ReferencesSearch.search(variable, variable.getUseScope(), false);
          query.forEach(reference -> {
            final PsiElement element = reference.getElement();
            final PsiElement parent = PsiTreeUtil.skipParentsOfType(element, PsiParenthesizedExpression.class);
            if (!(parent instanceof PsiReferenceExpression)) {
              return true;
            }
            final PsiElement grandParent = parent.getParent();
            if (!(grandParent instanceof PsiMethodCallExpression)) {
              return true;
            }
            final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)grandParent;
            final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
            final PsiExpression[] arguments = argumentList.getExpressions();
            for (PsiExpression argument : arguments) {
              argument.accept(this);
            }
            return true;
          });
          final PsiExpression initializer = variable.getInitializer();
          if (initializer != null) {
            initializer.accept(this);
          }
        }
        return;
      }
      if (ignoreGetMessage) {
        argumentsContainCatchParameter = true;
      }
      else {
        final PsiElement parent = expression.getParent();
        if (parent instanceof PsiReferenceExpression) {
          final PsiElement grandParent = parent.getParent();
          if (grandParent instanceof PsiMethodCallExpression) {
            return;
          }
        }
        argumentsContainCatchParameter = true;
      }
    }

    boolean usesParameter() {
      return argumentsContainCatchParameter;
    }
  }
}