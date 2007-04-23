package org.jetbrains.plugins.groovy.lang.psi.impl;

import org.jetbrains.plugins.groovy.lang.psi.GroovyElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiFile;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.components.ProjectComponent;

/**
 * @author ven
 */
public class GroovyElementFactoryImpl extends GroovyElementFactory implements ProjectComponent {
  Project myProject;

  public PsiElement createIdentifierFromText(String idText) {
    PsiFile file = createGroovyFile(idText);
    return ((GrReferenceExpression) ((GroovyFile) file).getStatements()[0]).getReferenceNameElement();
  }

  private PsiFile createGroovyFile(String idText) {
    return PsiManager.getInstance(myProject).getElementFactory().createFileFromText("__DUMMY", idText);
  }

  public void projectOpened() {}

  public void projectClosed() {}

  @NonNls
  @NotNull
  public String getComponentName() {
    return "Groovy Element Factory";
  }

  public void initComponent() {}

  public void disposeComponent() {}
}
