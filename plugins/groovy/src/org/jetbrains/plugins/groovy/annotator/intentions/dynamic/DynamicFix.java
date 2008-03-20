package org.jetbrains.plugins.groovy.annotator.intentions.dynamic;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ui.DynamicDialog;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ui.DynamicMethodDialog;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ui.DynamicPropertyDialog;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

/**
 * User: Dmitry.Krasilschikov
 * Date: 22.11.2007
 */
public class DynamicFix implements IntentionAction {
  private final GrReferenceExpression myReferenceExpression;
  private final boolean myIsMethod;

  public DynamicFix(boolean isMethod, GrReferenceExpression referenceExpression) {
    myIsMethod = isMethod;
    myReferenceExpression = referenceExpression;
  }

  @NotNull
  public String getText() {
    return !myIsMethod ? GroovyBundle.message("add.dynamic.property") : GroovyBundle.message("add.dynamic.method");
  }

  @NotNull
  public String getFamilyName() {
    return GroovyBundle.message("add.dynamic.element");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    return myReferenceExpression.isValid();
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
    DynamicDialog dialog = myIsMethod ?
                           new DynamicMethodDialog(myReferenceExpression) :
                           new DynamicPropertyDialog(myReferenceExpression);

    dialog.show();
  }

  public boolean startInWriteAction() {
    return false;
  }
}