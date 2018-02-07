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
package com.siyeh.ig.jdk;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettingsFacade;
import com.intellij.psi.util.InheritanceUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ForeachStatementInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("extended.for.statement.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("extended.for.statement.problem.descriptor");
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new ForEachFix();
  }

  private static class ForEachFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("extended.for.statement.replace.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiForeachStatement statement = (PsiForeachStatement)element.getParent();
      final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
      assert statement != null;
      final PsiExpression iteratedValue = statement.getIteratedValue();
      if (iteratedValue == null) {
        return;
      }
      CommentTracker tracker = new CommentTracker();
      @NonNls final StringBuilder newStatement = new StringBuilder();
      final PsiParameter iterationParameter = statement.getIterationParameter();
      boolean generateFinalLocals = JavaCodeStyleSettingsFacade.getInstance(project).isGenerateFinalLocals();
      tracker.markUnchanged(iteratedValue);
      if (iteratedValue.getType() instanceof PsiArrayType) {
        final PsiType type = iterationParameter.getType();
        final String index = codeStyleManager.suggestUniqueVariableName("i", statement, true);
        newStatement.append("for(int ").append(index).append(" = 0;");
        newStatement.append(index).append('<').append(iteratedValue.getText()).append(".length;");
        newStatement.append(index).append("++)").append("{ ");
        if (generateFinalLocals) {
          newStatement.append("final ");
        }
        newStatement.append(type.getCanonicalText()).append(' ').append(iterationParameter.getName());
        newStatement.append(" = ").append(iteratedValue.getText()).append('[').append(index).append("];");
      }
      else {
        @NonNls final StringBuilder methodCall = new StringBuilder();
        if (ParenthesesUtils.getPrecedence(iteratedValue) > ParenthesesUtils.METHOD_CALL_PRECEDENCE) {
          methodCall.append('(').append(iteratedValue.getText()).append(')');
        }
        else {
          methodCall.append(iteratedValue.getText());
        }
        methodCall.append(".iterator()");
        final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
        final PsiExpression iteratorCall = factory.createExpressionFromText(methodCall.toString(), iteratedValue);
        final PsiType variableType = GenericsUtil.getVariableTypeByExpressionType(iteratorCall.getType());
        if (variableType == null) {
          return;
        }
        final PsiType parameterType = iterationParameter.getType();
        final String typeText = parameterType.getCanonicalText();
        newStatement.append("for(").append(variableType.getCanonicalText()).append(' ');
        final String iterator = codeStyleManager.suggestUniqueVariableName("iterator", statement, true);
        newStatement.append(iterator).append("=").append(iteratorCall.getText()).append(';');
        newStatement.append(iterator).append(".hasNext();){");
        if (generateFinalLocals) {
          newStatement.append("final ");
        }
        newStatement.append(typeText).append(' ').append(iterationParameter.getName()).append(" = ").append(iterator).append(".next();");
      }
      final PsiStatement body = statement.getBody();
      if (body instanceof PsiBlockStatement) {
        final PsiBlockStatement blockStatement = (PsiBlockStatement)body;
        final PsiCodeBlock block = blockStatement.getCodeBlock();
        final PsiElement[] children = block.getChildren();
        for (int i = 1; i < children.length - 1; i++) {
          //skip the braces
          newStatement.append(tracker.text(children[i]));
        }
      }
      else {
        final String bodyText;
        if (body == null) {
          bodyText = "";
        }
        else {
          bodyText = tracker.text(body);
        }
        newStatement.append(bodyText);
      }
      newStatement.append('}');

      PsiReplacementUtil.replaceStatementAndShortenClassNames(statement, newStatement.toString(), tracker);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ForeachStatementVisitor();
  }

  private static class ForeachStatementVisitor extends BaseInspectionVisitor {

    @Override
    public void visitForeachStatement(@NotNull PsiForeachStatement statement) {
      super.visitForeachStatement(statement);
      final PsiExpression iteratedValue = statement.getIteratedValue();
      if (iteratedValue == null || !InheritanceUtil.isInheritor(iteratedValue.getType(), CommonClassNames.JAVA_LANG_ITERABLE)) {
        return;
      }
      registerStatementError(statement);
    }
  }
}