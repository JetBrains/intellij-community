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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.RefactoringMessageDialog;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;

import java.util.ArrayList;
import java.util.Collection;

/**
 * @author ilyas
 */
public class GroovyInlineHandler implements InlineHandler {

  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.refactoring.inline.GroovyInlineHandler");

  private static final String REFACTORING_NAME = GroovyRefactoringBundle.message("inline.variable.title");

  @Nullable
  public Settings prepareInlineElement(final PsiElement element, Editor editor, boolean invokedOnReference) {
    if (!invokedOnReference) return null;

    if (element instanceof GrVariable &&
        GroovyRefactoringUtil.isLocalVariable((GrVariable) element)) { // todo add method && class
      return inlineLocalVariableSettings((GrVariable) element, editor);
    } else {
      String message = GroovyRefactoringBundle.message("wrong.element.to.inline");
      CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.INLINE_VARIABLE, element.getProject());
    }

    return null;
  }

  /**
   * Returns Settings object for referenced definition in case of local variable
   *
   * @param variable
   * @param editor
   * @return
   */
  private Settings inlineLocalVariableSettings(final GrVariable variable, Editor editor) {
    final String localName = variable.getNameIdentifierGroovy().getText();
    final Project project = variable.getProject();
    final Collection<PsiReference> refs = ReferencesSearch.search(variable, GlobalSearchScope.projectScope(variable.getProject()), false).findAll();
    ArrayList<PsiElement> exprs = new ArrayList<PsiElement>();
    for (PsiReference ref : refs) {
      exprs.add(ref.getElement());
    }

    GroovyRefactoringUtil.highlightOccurences(project, editor, exprs.toArray(PsiElement.EMPTY_ARRAY));
    if (variable.getInitializerGroovy() == null) {
      String message = GroovyRefactoringBundle.message("cannot.find.a.single.definition.to.inline.local.var");
      CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.INLINE_VARIABLE, variable.getProject());
      return null;
    }
    if (refs.isEmpty()) {
      String message = GroovyRefactoringBundle.message("variable.is.never.used.0", variable);
      CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.INLINE_VARIABLE, variable.getProject());
      return null;
    }

    final String question = GroovyRefactoringBundle.message("inline.local.variable.prompt.0.1", localName, refs.size());
    RefactoringMessageDialog dialog = new RefactoringMessageDialog(
        REFACTORING_NAME,
        question,
        HelpID.INLINE_VARIABLE,
        "OptionPane.questionIcon",
        true,
        project);
    dialog.show();
    if (!dialog.isOK()) {
      WindowManager.getInstance().getStatusBar(project).setInfo(GroovyRefactoringBundle.message("press.escape.to.remove.the.highlighting"));
      return null;
    }

    return new Settings() {
      public boolean isOnlyOneReferenceToInline() {
        return refs.size() == 1;
      }
    };
  }

  public void removeDefinition(final PsiElement element) {
/*
    Runnable runnable = new Runnable() {
      public void run() {
        try {
          if (element instanceof GrVariable) {
            ((GrVariable) element).removeVariable();
          }
        } catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    };

    CommandProcessor.getInstance().executeCommand(referenced.getProject(), new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(runnable);
      }
    }, GroovyRefactoringBundle.message("inline.local.command", variable.getNameIdentifierGroovy().getText()), null);

*/
  }

  @Nullable
  public Inliner createInliner(PsiElement element) {
    if (element instanceof GrVariable &&
        GroovyRefactoringUtil.isLocalVariable((GrVariable) element)) {
      return createInlinerForLocalVariable(((GrVariable) element));
    }
    return null;
  }

  /**
   * Creates new inliner for local variable occurences
   *
   * @param variable
   * @return
   */
  private Inliner createInlinerForLocalVariable(final GrVariable variable) {
    return new Inliner() {

      @Nullable
      public Collection<String> getConflicts(PsiReference reference, PsiElement referenced) {
        return null;
      }

      public void inlineReference(final PsiReference reference, PsiElement referenced) {
        assert reference instanceof GrExpression;
        assert variable.getInitializerGroovy() != null;
        final Runnable runnable = new Runnable() {
          public void run() {
            try {
              GrExpression expr = GroovyElementFactory.getInstance(variable.getProject()).
                  createExpressionFromText(variable.getInitializerGroovy().getText());
              ((GrExpression) reference).replaceWithExpression(expr);
            } catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        };

        CommandProcessor.getInstance().executeCommand(referenced.getProject(), new Runnable() {
          public void run() {
            ApplicationManager.getApplication().runWriteAction(runnable);
          }
        }, GroovyRefactoringBundle.message("inline.local.command", variable.getNameIdentifierGroovy().getText()), null);

      }
    };
  }
}

