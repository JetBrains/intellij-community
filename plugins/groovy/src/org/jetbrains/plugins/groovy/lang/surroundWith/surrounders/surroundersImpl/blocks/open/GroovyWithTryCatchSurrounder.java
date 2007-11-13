package org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.blocks.open;

import com.intellij.psi.PsiElement;

/**
 * User: Dmitry.Krasilschikov
 * Date: 22.05.2007
 */
public class GroovyWithTryCatchSurrounder extends GroovyWithTrySurrounder {
  public String getTemplateDescription() {
    return super.getTemplateDescription() + " / catch";
  }

  protected String getElementsTemplateAsString(PsiElement[] nodes) {
    return super.getElementsTemplateAsString(nodes) + " catch (exception) { \n }";
  }
}
