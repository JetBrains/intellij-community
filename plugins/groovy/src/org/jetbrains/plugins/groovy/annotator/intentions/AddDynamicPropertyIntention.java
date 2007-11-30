package org.jetbrains.plugins.groovy.annotator.intentions;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DynamicPropertiesManager;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.properties.DynamicPropertyBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

/**
 * User: Dmitry.Krasilschikov
 * Date: 22.11.2007
 */
public class AddDynamicPropertyIntention implements IntentionAction {
  private final GrReferenceExpression myReferenceExpression;

  public AddDynamicPropertyIntention(GrReferenceExpression referenceExpression) {
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
    PsiElement dynamicValueTypeDefinition;
    final DynamicPropertiesManager dynamicPropertiesManager = DynamicPropertiesManager.getInstance(project);
    Module module = ProjectRootManager.getInstance(project).getFileIndex().getModuleForFile(myReferenceExpression.getContainingFile().getVirtualFile());

    if (myReferenceExpression.isQualified()) {
      final PsiReference qualifierReference = myReferenceExpression.getQualifierExpression().getReference();

      if (qualifierReference == null) return;
      dynamicValueTypeDefinition = qualifierReference.resolve();

      if (!(dynamicValueTypeDefinition instanceof PsiClass)) return;

    } else {
      PsiElement refParent = myReferenceExpression.getParent();

      while (refParent != null && !(refParent instanceof GroovyFileBase)) {
        refParent = refParent.getParent();
      }

      if (refParent == null) return;
      dynamicValueTypeDefinition = ((GroovyFileBase) refParent).getScriptClass();
    }

    dynamicPropertiesManager.addDynamicProperty(
        new DynamicPropertyBase(myReferenceExpression.getName(), ((PsiClass) dynamicValueTypeDefinition).getQualifiedName(), module.getName()));
  }

  public boolean startInWriteAction() {
    return true;
  }
}