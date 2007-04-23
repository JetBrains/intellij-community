package org.jetbrains.plugins.groovy.lang.psi;

import com.intellij.psi.PsiElement;
import com.intellij.openapi.project.Project;

/**
 * @author ven
 */
public abstract class GroovyElementFactory {
  public static GroovyElementFactory getInstance(Project project) {
    return project.getComponent(GroovyElementFactory.class);
  }

  public abstract PsiElement createIdentifierFromText(String idText);
}
