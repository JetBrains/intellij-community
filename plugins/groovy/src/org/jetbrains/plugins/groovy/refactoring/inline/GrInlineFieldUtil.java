/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.psi.PsiModifier;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;

/**
 * @author Max Medvedev
 */
public class GrInlineFieldUtil {

  public static final String INLINE_FIELD = RefactoringBundle.message("inline.field.title");

  private GrInlineFieldUtil() {
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
}
