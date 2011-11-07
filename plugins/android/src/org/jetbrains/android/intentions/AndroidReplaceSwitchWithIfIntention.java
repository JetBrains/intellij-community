package org.jetbrains.android.intentions;

import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.switchtoif.ReplaceSwitchWithIfIntention;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidReplaceSwitchWithIfIntention implements IntentionAction, HighPriorityAction {
  private final ReplaceSwitchWithIfIntention myBaseIntention = new ReplaceSwitchWithIfIntention();
  
  @NotNull
  @Override
  public String getText() {
    return myBaseIntention.getText();
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return AndroidBundle.message("intention.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (file == null) {
      return false;
    }

    final AndroidFacet facet = AndroidFacet.getInstance(file);
    if (facet == null || !facet.getConfiguration().LIBRARY_PROJECT) {
      return false;
    }
    
    final PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    if (element == null) {
      return false;
    }

    final PsiSwitchLabelStatement switchLabelStatement = PsiTreeUtil.getParentOfType(element, PsiSwitchLabelStatement.class);
    if (switchLabelStatement == null) {
      return false;
    }

    final PsiExpression caseValue = switchLabelStatement.getCaseValue();
    if (!(caseValue instanceof PsiReferenceExpression) || !PsiTreeUtil.isAncestor(caseValue, element, false)) {
      return false;
    }

    if (myBaseIntention.isAvailable(project, editor, file)) {
      // this intention is addition to the base one
      return false;
    }

    final PsiSwitchStatement switchStatement = PsiTreeUtil.getParentOfType(switchLabelStatement, PsiSwitchStatement.class);
    if (switchStatement == null || !ReplaceSwitchWithIfIntention.canProcess(switchStatement)) {
      return false;
    }

    final PsiElement resolvedElement = ((PsiReferenceExpression)caseValue).resolve();
    if (resolvedElement == null || !(resolvedElement instanceof PsiField)) {
      return false;
    }

    final PsiField resolvedField = (PsiField)resolvedElement;
    final PsiFile containingFile = resolvedField.getContainingFile();

    if (containingFile == null || !AndroidResourceUtil.isRJavaField(containingFile, resolvedField)) {
      return false;
    }

    final PsiModifierList modifierList = resolvedField.getModifierList();

    return modifierList == null || !modifierList.hasModifierProperty(PsiModifier.FINAL);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    assert element != null;
    
    final PsiSwitchStatement switchStatement = PsiTreeUtil.getParentOfType(element, PsiSwitchStatement.class);
    assert switchStatement != null;

    ReplaceSwitchWithIfIntention.doProcessIntention(switchStatement);
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
