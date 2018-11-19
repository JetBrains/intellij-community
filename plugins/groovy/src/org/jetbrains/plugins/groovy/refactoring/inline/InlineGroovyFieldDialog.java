// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.refactoring.inline;

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

  InlineGroovyFieldDialog(Project project, PsiField field, boolean invokedOnReference) {
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
  protected String getHelpId() {
    return HelpID.INLINE_FIELD;
  }
}