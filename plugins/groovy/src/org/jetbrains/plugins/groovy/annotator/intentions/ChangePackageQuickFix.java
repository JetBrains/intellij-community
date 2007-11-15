package org.jetbrains.plugins.groovy.annotator.intentions;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;

/**
 * User: Dmitry.Krasilschikov
  * Date: 01.11.2007
  */
 public class ChangePackageQuickFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.annotator.intentions.ChangePackageQuickFix");

  private final GrPackageDefinition myPackageDefinition;
  private final String myNewPackageName;

  public ChangePackageQuickFix(GrPackageDefinition packageDefinition, String newPackageName) {
    myPackageDefinition = packageDefinition;
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
    return myPackageDefinition.getContainingFile().isValid() && myPackageDefinition.getManager().isInProject(file);
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
    final GrReferenceElement newPackageReference = GroovyElementFactory.getInstance(myPackageDefinition.getProject()).createPackegeReferenceElementFromText(myNewPackageName);
    myPackageDefinition.replacePackageReference(newPackageReference);
  }

  public boolean startInWriteAction() {
    return true;
  }
}
