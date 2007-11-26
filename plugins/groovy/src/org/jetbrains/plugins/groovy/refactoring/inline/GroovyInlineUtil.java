/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.refactoring.inline;

import com.intellij.lang.refactoring.InlineHandler;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiElement;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpression;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementFactory;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.ArrayList;

/**
 * @author ilyas
 */
public class GroovyInlineUtil {

  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.refactoring.inline.GroovyInlineUtil");


  /**
   * Creates new inliner for local variable occurences
   */
  static InlineHandler.Inliner createInlinerForLocalVariable(final GrVariable variable) {
    return new InlineHandler.Inliner() {

      @Nullable
      public Collection<String> getConflicts(PsiReference reference, PsiElement referenced) {
        ArrayList<String> conflicts = new ArrayList<String>();
        GrExpression expr = (GrExpression) reference.getElement();
        if (expr.getParent() instanceof GrAssignmentExpression) {
          GrAssignmentExpression parent = (GrAssignmentExpression) expr.getParent();
          if (expr.equals(parent.getLValue())) {
            conflicts.add(GroovyRefactoringBundle.message("local.varaible.is.lvalue"));
          }
        }
        return conflicts;
      }

      public void inlineReference(final PsiReference reference, final PsiElement referenced) {
        GrExpression exprToBeReplaced = (GrExpression) reference.getElement();
        assert variable.getInitializerGroovy() != null;
        GrExpression initializerGroovy = variable.getInitializerGroovy();
        assert initializerGroovy != null;
        GrExpression tempExpr = initializerGroovy;
        while (tempExpr instanceof GrParenthesizedExpression) {
          tempExpr = ((GrParenthesizedExpression) tempExpr).getOperand();
        }
        Project project = variable.getProject();
        GroovyElementFactory factory = GroovyElementFactory.getInstance(project);
        GrExpression newExpr = factory.createExpressionFromText(tempExpr.getText());

        try {
          newExpr = exprToBeReplaced.replaceWithExpression(newExpr, true);
          FileEditorManager manager = FileEditorManager.getInstance(project);
          Editor editor = manager.getSelectedTextEditor();
          GroovyRefactoringUtil.highlightOccurrences(project, editor, new PsiElement[]{newExpr});
          WindowManager.getInstance().getStatusBar(project).setInfo(GroovyRefactoringBundle.message("press.escape.to.remove.the.highlighting"));
        } catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    };
  }
}
