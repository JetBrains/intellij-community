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
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.virtual.DynamicVirtualMethod;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.virtual.DynamicVirtualProperty;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

/**
 * User: Dmitry.Krasilschikov
 * Date: 22.11.2007
 */
public class DynamicFix implements IntentionAction {
  private final DynamicVirtualElement myDynamicElement;
  private final GrReferenceExpression myReferenceExpression;

  public DynamicFix(DynamicVirtualElement virtualElement, GrReferenceExpression referenceExpression) {
    myDynamicElement = virtualElement;
    myReferenceExpression = referenceExpression;
  }

  @NotNull
  public String getText() {
    if (myDynamicElement instanceof DynamicVirtualProperty) {
      return GroovyBundle.message("add.dynamic.property");
    } else if (myDynamicElement instanceof DynamicVirtualMethod) {
      return GroovyBundle.message("add.dynamic.method");
    }
    return "nothing";
  }

  @NotNull
  public String getFamilyName() {
    return GroovyBundle.message("add.dynamic.element");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    return true;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
    DynamicDialog dialog = null;
    if (myDynamicElement instanceof DynamicVirtualProperty) {
      dialog = new DynamicPropertyDialog(project, ((DynamicVirtualProperty) myDynamicElement), myReferenceExpression);
    } else if (myDynamicElement instanceof DynamicVirtualMethod) {
      dialog = new DynamicMethodDialog(project, ((DynamicVirtualMethod) myDynamicElement), myReferenceExpression);
    }

    if (dialog == null) return;
    dialog.show();
  }

  public boolean startInWriteAction() {
    return true;
  }
}