package org.jetbrains.plugins.groovy.annotator.intentions;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

/**
 * User: Dmitry.Krasilschikov
  * Date: 01.11.2007
  */
 public class ChangePackageQuickFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.annotator.intentions.ChangePackageQuickFix");

  private final GroovyFile myFile;
  private final String myNewPackageName;

  public ChangePackageQuickFix(GroovyFile file, String newPackageName) {
    myFile = file;
    myNewPackageName = newPackageName;
  }

  @NotNull
  public String getText() {
    return GroovyBundle.message("fix.package.name");
  }

  @NotNull
  public String getFamilyName() {
    return GroovyBundle.message("fix.package.name");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myFile.isValid() && myFile.getManager().isInProject(file);
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
    myFile.setPackageName(myNewPackageName);
  }

  public boolean startInWriteAction() {
    return true;
  }
}
