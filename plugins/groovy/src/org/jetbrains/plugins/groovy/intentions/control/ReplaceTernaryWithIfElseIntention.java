/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.intentions.control;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrConditionalExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

/**
 * @author Andreas Arledal
 */
public class ReplaceTernaryWithIfElseIntention extends Intention {

  @Override
  protected void processIntention(@NotNull PsiElement element, Project project, Editor editor) throws IncorrectOperationException {

    GrConditionalExpression parentTernary = findTernary(element);
    GroovyPsiElementFactory groovyPsiElementFactory = GroovyPsiElementFactory.getInstance(project);

    GrReturnStatement parentReturn = (GrReturnStatement)parentTernary.getParent();

    String condition = parentTernary.getCondition().getText();
    GrExpression thenBranch = parentTernary.getThenBranch();
    String thenText = thenBranch != null ? thenBranch.getText() : "";

    GrExpression elseBranch = parentTernary.getElseBranch();
    String elseText = elseBranch != null ? elseBranch.getText() : "";

    String text = "if (" + condition + ") { \nreturn " + thenText + "\n} else {\n return " + elseText + "\n}";
    GrIfStatement ifStatement = (GrIfStatement)groovyPsiElementFactory.createStatementFromText(text);
    ifStatement = parentReturn.replaceWithStatement(ifStatement);
    editor.getCaretModel().moveToOffset(ifStatement.getRParenth().getTextRange().getEndOffset());


  }

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new PsiElementPredicate() {
      @Override
      public boolean satisfiedBy(PsiElement element) {
        GrConditionalExpression ternary = findTernary(element);
        if (ternary == null || element == null || ternary.getThenBranch() == null || ternary.getElseBranch() == null) {
          return false;
        }
        if (!(ternary.getParent() instanceof GrReturnStatement)) {
          return false;
        }
        return true;
      }
    };
  }

  @Nullable
  private static GrConditionalExpression findTernary(PsiElement element) {
    GrConditionalExpression ternary = PsiTreeUtil.getParentOfType(element, GrConditionalExpression.class);
    if (ternary == null) {
      GrReturnStatement ret = PsiTreeUtil.getParentOfType(element, GrReturnStatement.class);
      if (ret != null && ret.getReturnValue() instanceof GrConditionalExpression) {
        ternary = (GrConditionalExpression)ret.getReturnValue();
      }
    }
    return ternary;
  }
}
