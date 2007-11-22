package org.jetbrains.plugins.groovy.annotator.intentions;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

/**
 * User: Dmitry.Krasilschikov
 * Date: 22.11.2007
 */
public class AddDynamicAttributes implements IntentionAction {
  private final GrReferenceExpression myReferenceExpression;

  public AddDynamicAttributes(GrReferenceExpression referenceExpression) {
    myReferenceExpression = referenceExpression;
  }

  @NotNull
  public String getText() {
    return GroovyBundle.message("add.dynamic.attributes");
  }

  @NotNull
  public String getFamilyName() {
    return GroovyBundle.message("add.dynamic.attributes");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    return true;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
//    TODO:NIY
  }

  public boolean startInWriteAction() {
    return true;
  }
}