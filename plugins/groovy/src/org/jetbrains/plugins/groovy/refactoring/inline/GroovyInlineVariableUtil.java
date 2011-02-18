/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.refactoring.inline;

import com.intellij.lang.refactoring.InlineHandler;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.RefactoringMessageDialog;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;

import java.util.ArrayList;
import java.util.Collection;

import static org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.skipParentheses;

/**
 * @author ilyas
 */
public class GroovyInlineVariableUtil {

  public static final String REFACTORING_NAME = GroovyRefactoringBundle.message("inline.variable.title");

  private GroovyInlineVariableUtil() {
  }


  /**
   * Creates new inliner for local variable occurences
   */
  static InlineHandler.Inliner createInlinerForVariable(final GrVariable variable) {
    return new InlineHandler.Inliner() {

      @Nullable
      public MultiMap<PsiElement, String> getConflicts(PsiReference reference, PsiElement referenced) {
        MultiMap<PsiElement, String> conflicts = new MultiMap<PsiElement, String>();
        GrExpression expr = (GrExpression)reference.getElement();
        if (expr.getParent() instanceof GrAssignmentExpression) {
          GrAssignmentExpression parent = (GrAssignmentExpression)expr.getParent();
          if (expr.equals(parent.getLValue())) {
            conflicts.putValue(expr, GroovyRefactoringBundle.message("local.varaible.is.lvalue"));
          }
        }
        return conflicts;
      }

      public void inlineUsage(final UsageInfo usage, final PsiElement referenced) {
        GrExpression exprToBeReplaced = (GrExpression)usage.getElement();
        if (exprToBeReplaced == null) return;
        assert variable.getInitializerGroovy() != null;
        GrExpression initializerGroovy = variable.getInitializerGroovy();
        assert initializerGroovy != null;
        GrExpression tempExpr = (GrExpression)skipParentheses(initializerGroovy, false);
        if (tempExpr == null) return;
        Project project = variable.getProject();
        GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);
        GrExpression newExpr;
        newExpr = factory.createExpressionFromText(tempExpr.getText());
        newExpr = exprToBeReplaced.replaceWithExpression(newExpr, true);
        FileEditorManager manager = FileEditorManager.getInstance(project);
        Editor editor = manager.getSelectedTextEditor();
        GroovyRefactoringUtil.highlightOccurrences(project, editor, new PsiElement[]{newExpr});
        WindowManager.getInstance().getStatusBar(project)
          .setInfo(GroovyRefactoringBundle.message("press.escape.to.remove.the.highlighting"));
      }
    };
  }

  /**
   * Returns Settings object for referenced definition in case of local variable
   */
  @Nullable
  static InlineHandler.Settings inlineLocalVariableSettings(final GrVariable variable, Editor editor) {
    final String localName = variable.getName();
    final Project project = variable.getProject();
    final Collection<PsiReference> refs = ReferencesSearch.search(variable).findAll();
    ArrayList<PsiElement> exprs = new ArrayList<PsiElement>();
    for (PsiReference ref : refs) {
      exprs.add(ref.getElement());
    }

    GroovyRefactoringUtil.highlightOccurrences(project, editor, PsiUtilBase.toPsiElementArray(exprs));
    if (variable.getInitializerGroovy() == null) {
      String message = GroovyRefactoringBundle.message("cannot.find.a.single.definition.to.inline.local.var");
      CommonRefactoringUtil.showErrorHint(variable.getProject(), editor, message, REFACTORING_NAME, HelpID.INLINE_VARIABLE);
      return null;
    }
    if (refs.isEmpty()) {
      String message = GroovyRefactoringBundle.message("variable.is.never.used.0", variable.getName());
      CommonRefactoringUtil.showErrorHint(variable.getProject(), editor, message, REFACTORING_NAME, HelpID.INLINE_VARIABLE);
      return null;
    }

    return inlineDialogResult(localName, project, refs, false, false);
  }

  /**
   * Shows dialog with question to inline
   */
  @Nullable
  private static InlineHandler.Settings inlineDialogResult(String localName,
                                                           Project project,
                                                           Collection<PsiReference> refs,
                                                           boolean forField,
                                                           final boolean inlineOnlyOneReference) {
    Application application = ApplicationManager.getApplication();
    if (!application.isUnitTestMode()) {
      final String question =
        GroovyRefactoringBundle.message(forField ? "inline.field.prompt.0.1" : "inline.local.variable.prompt.0.1", localName, refs.size());
      RefactoringMessageDialog dialog =
        new RefactoringMessageDialog(REFACTORING_NAME, question, forField ? HelpID.INLINE_FIELD : HelpID.INLINE_VARIABLE,
                                     "OptionPane.questionIcon", true, project);
      dialog.show();
      if (!dialog.isOK()) {
        WindowManager.getInstance().getStatusBar(project)
          .setInfo(GroovyRefactoringBundle.message("press.escape.to.remove.the.highlighting"));
        return null;
      }
    }

    return new InlineHandler.Settings() {
      public boolean isOnlyOneReferenceToInline() {
        return inlineOnlyOneReference;
      }
    };
  }

  @Nullable
  static InlineHandler.Settings inlineFieldSettings(final GrField field, Editor editor) {
    final String localName = field.getName();
    final Project project = field.getProject();

    if (!field.hasModifierProperty(GrModifier.FINAL)) {
      String message = RefactoringBundle.message("0.refactoring.is.supported.only.for.final.fields", REFACTORING_NAME);
      CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.INLINE_FIELD);
      return null;
    }

    if (field.getInitializerGroovy() == null) {
      String message = GroovyRefactoringBundle.message("cannot.find.a.single.definition.to.inline.field");
      CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.INLINE_FIELD);
      return null;
    }

    final Collection<PsiReference> refs = ReferencesSearch.search(field).findAll();
    ArrayList<PsiElement> exprs = new ArrayList<PsiElement>();
    for (PsiReference ref : refs) {
      exprs.add(ref.getElement());
    }
    GroovyRefactoringUtil.highlightOccurrences(project, editor, PsiUtilBase.toPsiElementArray(exprs));
    if (refs.isEmpty()) {
      String message = GroovyRefactoringBundle.message("field.is.never.used.0", field.getName());
      CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.INLINE_FIELD);
      return null;
    }

    return inlineDialogResult(localName, project, refs, true, false);
  }
}
