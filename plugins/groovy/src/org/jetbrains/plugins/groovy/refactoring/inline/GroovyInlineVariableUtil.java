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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.PsiModifier;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.RefactoringMessageDialog;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;

/**
 * @author ilyas
 */
public class GroovyInlineVariableUtil {

  public static final String INLINE_VARIABLE = RefactoringBundle.message("inline.variable.title");
  public static final String INLINE_FIELD = RefactoringBundle.message("inline.field.title");

  private GroovyInlineVariableUtil() {
  }


  /**
   * Creates new inliner for local variable occurences
   */
  static InlineHandler.Inliner createInlinerForVariable(final GrVariable variable) {
    return new GrVariableInliner(variable);
  }

  /**
   * Returns Settings object for referenced definition in case of local variable
   */
  @Nullable
  static InlineHandler.Settings inlineLocalVariableSettings(final GrVariable variable, Editor editor) {
    final String localName = variable.getName();
    final Project project = variable.getProject();

    if (variable.getInitializerGroovy() == null) {
      String message = GroovyRefactoringBundle.message("cannot.find.a.single.definition.to.inline.local.var");
      CommonRefactoringUtil.showErrorHint(variable.getProject(), editor, message, INLINE_VARIABLE, HelpID.INLINE_VARIABLE);
      return InlineHandler.Settings.CANNOT_INLINE_SETTINGS;
    }

    return inlineLocalVarDialogResult(localName, project);
  }

  /**
   * Shows dialog with question to inline
   */
  @Nullable
  private static InlineHandler.Settings inlineLocalVarDialogResult(String localName, Project project) {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      final String question = GroovyRefactoringBundle.message("inline.local.variable.prompt", localName);
      RefactoringMessageDialog dialog = new RefactoringMessageDialog(INLINE_VARIABLE, question, HelpID.INLINE_VARIABLE, "OptionPane.questionIcon", true, project);
      dialog.show();
      if (!dialog.isOK()) {
        WindowManager.getInstance().getStatusBar(project).setInfo(GroovyRefactoringBundle.message("press.escape.to.remove.the.highlighting"));
        return InlineHandler.Settings.CANNOT_INLINE_SETTINGS;
      }
    }

    return new InlineHandler.Settings() {
      public boolean isOnlyOneReferenceToInline() {
        return false;
      }
    };
  }

  @Nullable
  static InlineHandler.Settings inlineFieldSettings(final GrField field, Editor editor, boolean invokedOnReference) {
    final Project project = field.getProject();

    if (!field.hasModifierProperty(PsiModifier.FINAL)) {
      String message = RefactoringBundle.message("0.refactoring.is.supported.only.for.final.fields", INLINE_FIELD);
      CommonRefactoringUtil.showErrorHint(project, editor, message, INLINE_FIELD, HelpID.INLINE_FIELD);
      return InlineHandler.Settings.CANNOT_INLINE_SETTINGS;
    }

    if (field.getInitializerGroovy() == null) {
      String message = GroovyRefactoringBundle.message("cannot.find.a.single.definition.to.inline.field");
      CommonRefactoringUtil.showErrorHint(project, editor, message, INLINE_FIELD, HelpID.INLINE_FIELD);
      return InlineHandler.Settings.CANNOT_INLINE_SETTINGS;
    }

    return inlineFieldDialogResult(project, field, invokedOnReference);
  }

  private static InlineHandler.Settings inlineFieldDialogResult(Project project, GrField field, final boolean invokedOnReference) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return new InlineHandler.Settings() {
        @Override
        public boolean isOnlyOneReferenceToInline() {
          return invokedOnReference;
        }
      };
    }

    final InlineGroovyFieldDialog dialog = new InlineGroovyFieldDialog(project, field, invokedOnReference);
    dialog.show();
    if (dialog.isOK()) {
      return new InlineHandler.Settings() {
        @Override
        public boolean isOnlyOneReferenceToInline() {
          return dialog.isInlineThisOnly();
        }
      };
    }
    else {
      WindowManager.getInstance().getStatusBar(project).setInfo(GroovyRefactoringBundle.message("press.escape.to.remove.the.highlighting"));
      return InlineHandler.Settings.CANNOT_INLINE_SETTINGS;
    }
  }
}
