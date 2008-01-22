package org.jetbrains.plugins.groovy.annotator.intentions;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.properties.real.DynamicProperty;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ui.DynamicPropertyDialog;

/**
 * User: Dmitry.Krasilschikov
 * Date: 22.11.2007
 */
public class DynamicPropertyIntention implements IntentionAction {
  private final DynamicProperty myDynProperty;
  private final GrReferenceExpression myReferenceExpression;

  public DynamicPropertyIntention(DynamicProperty dynamicProperty, GrReferenceExpression referenceExpression) {
    myDynProperty = dynamicProperty;
    myReferenceExpression = referenceExpression;
  }

  @NotNull
  public String getText() {
    return GroovyBundle.message("add.dynamic.property");
  }

  @NotNull
  public String getFamilyName() {
    return GroovyBundle.message("add.dynamic.property");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    return true;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
//    final DynamicPropertiesManager dynamicPropertiesManager = DynamicPropertiesManager.getInstance(project);

    final DynamicPropertyDialog dialog = new DynamicPropertyDialog(project, myDynProperty, myReferenceExpression);
    dialog.show();
  }

  public boolean startInWriteAction() {
    return true;
  }
}