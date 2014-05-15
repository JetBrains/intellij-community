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

import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.inline.InlineOptionsDialog;

/**
* @author Max Medvedev
*/
class InlineGroovyFieldDialog extends InlineOptionsDialog {

  public static final String REFACTORING_NAME = RefactoringBundle.message("inline.field.title");

  private final PsiField myField;

  public InlineGroovyFieldDialog(Project project, PsiField field, boolean invokedOnReference) {
    super(project, true, field);
    myField = field;
    myInvokedOnReference = invokedOnReference;

    setTitle(REFACTORING_NAME);

    init();
  }

  @Override
  protected String getNameLabelText() {
    @SuppressWarnings("StaticFieldReferencedViaSubclass")
    String fieldText = PsiFormatUtil.formatVariable(myField, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE, PsiSubstitutor.EMPTY);
    return RefactoringBundle.message("inline.field.field.name.label", fieldText);
  }

  @Override
  protected String getBorderTitle() {
    return RefactoringBundle.message("inline.field.border.title");
  }

  @Override
  protected String getInlineThisText() {
    return RefactoringBundle.message("this.reference.only.and.keep.the.field");
  }

  @Override
  protected String getInlineAllText() {
    return RefactoringBundle.message("all.references.and.remove.the.field");
  }

  @Override
  protected boolean isInlineThis() {
    return JavaRefactoringSettings.getInstance().INLINE_FIELD_THIS;
  }

  @Override
  protected void doAction() {
    if (getOKAction().isEnabled()) {
      JavaRefactoringSettings settings = JavaRefactoringSettings.getInstance();
      if (myRbInlineThisOnly.isEnabled() && myRbInlineAll.isEnabled()) {
        settings.INLINE_FIELD_THIS = isInlineThisOnly();
      }
      close(OK_EXIT_CODE);
    }
  }

  @Override
  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.INLINE_FIELD);
  }
}
