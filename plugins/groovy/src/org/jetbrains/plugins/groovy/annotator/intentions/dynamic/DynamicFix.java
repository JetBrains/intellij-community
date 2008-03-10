package org.jetbrains.plugins.groovy.annotator.intentions.dynamic;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ui.DynamicDialog;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ui.DynamicMethodDialog;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ui.DynamicPropertyDialog;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements.DItemElement;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements.DPropertyElement;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements.DMethodElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

/**
 * User: Dmitry.Krasilschikov
 * Date: 22.11.2007
 */
public class DynamicFix implements IntentionAction {
  private final DItemElement myItemElement;
  private final GrReferenceExpression myReferenceExpression;

  public DynamicFix(DItemElement itemElement, GrReferenceExpression referenceExpression) {
    myItemElement = itemElement;
    myReferenceExpression = referenceExpression;
  }

  @NotNull
  public String getText() {
    if (myItemElement instanceof DPropertyElement) {
      return GroovyBundle.message("add.dynamic.property");
    } else if (myItemElement instanceof DMethodElement) {
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

    VirtualFile file;
    if (psiFile != null) {
      file = psiFile.getVirtualFile();
      if (file == null) return;
    } else return;

    final Module module = ProjectRootManager.getInstance(project).getFileIndex().getModuleForFile(file);
    if (module == null) return;

    if (myItemElement instanceof DPropertyElement) {
      dialog = new DynamicPropertyDialog(module, ((DPropertyElement) myItemElement), myReferenceExpression);
    } else if (myItemElement instanceof DMethodElement) {
      dialog = new DynamicMethodDialog(module, ((DMethodElement) myItemElement), myReferenceExpression);
    }

    if (dialog == null) return;
    dialog.show();
  }

  public boolean startInWriteAction() {
    return true;
  }
}