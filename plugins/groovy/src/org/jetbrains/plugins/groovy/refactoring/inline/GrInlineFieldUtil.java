// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.refactoring.inline;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.lang.refactoring.InlineHandler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiModifier;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;

/**
 * @author Max Medvedev
 */
public final class GrInlineFieldUtil {
  private GrInlineFieldUtil() {
  }

  @Nullable
  static InlineHandler.Settings inlineFieldSettings(final GrField field, Editor editor, boolean invokedOnReference) {
    final Project project = field.getProject();

    if (!field.hasModifierProperty(PsiModifier.FINAL)) {
      String message = JavaRefactoringBundle.message("0.refactoring.is.supported.only.for.final.fields", getInlineField());
      CommonRefactoringUtil.showErrorHint(project, editor, message, getInlineField(), HelpID.INLINE_FIELD);
      return InlineHandler.Settings.CANNOT_INLINE_SETTINGS;
    }

    if (field.getInitializerGroovy() == null) {
      String message = GroovyRefactoringBundle.message("cannot.find.a.single.definition.to.inline.field");
      CommonRefactoringUtil.showErrorHint(project, editor, message, getInlineField(), HelpID.INLINE_FIELD);
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
    if (dialog.showAndGet()) {
      return new InlineHandler.Settings() {
        @Override
        public boolean isOnlyOneReferenceToInline() {
          return dialog.isInlineThisOnly();
        }
      };
    }
    else {
      return InlineHandler.Settings.CANNOT_INLINE_SETTINGS;
    }
  }

  public static String getInlineField() {
    return JavaRefactoringBundle.message("inline.field.title");
  }
}
