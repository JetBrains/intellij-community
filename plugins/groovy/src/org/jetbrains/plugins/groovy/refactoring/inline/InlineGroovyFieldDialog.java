// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.inline;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.inline.InlineOptionsDialog;
import org.jetbrains.annotations.Nls;

import static org.jetbrains.annotations.Nls.Capitalization.Title;

/**
* @author Max Medvedev
*/
class InlineGroovyFieldDialog extends InlineOptionsDialog {
  private final PsiField myField;

  InlineGroovyFieldDialog(Project project, PsiField field, boolean invokedOnReference) {
    super(project, true, field);
    myField = field;
    myInvokedOnReference = invokedOnReference;

    setTitle(getRefactoringName());

    init();
  }

  @Override
  protected String getNameLabelText() {
    int options = myInvokedOnReference
                  ? PsiFormatUtilBase.SHOW_CONTAINING_CLASS | PsiFormatUtilBase.SHOW_NAME 
                  : PsiFormatUtilBase.SHOW_NAME;
    String fieldText = PsiFormatUtil.formatVariable(myField, options, PsiSubstitutor.EMPTY);
    return JavaRefactoringBundle.message("inline.field.field.name.label", fieldText);
  }

  @Override
  protected String getInlineThisText() {
    return JavaRefactoringBundle.message("this.reference.only.and.keep.the.field");
  }

  @Override
  protected String getInlineAllText() {
    return JavaRefactoringBundle.message("all.references.and.remove.the.field");
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

  public static @Nls(capitalization = Title) String getRefactoringName() {
    return JavaRefactoringBundle.message("inline.field.title");
  }
}