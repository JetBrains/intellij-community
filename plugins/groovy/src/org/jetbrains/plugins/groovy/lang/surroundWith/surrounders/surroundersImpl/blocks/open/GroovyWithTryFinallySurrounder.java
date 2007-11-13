package org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.blocks.open;

import com.intellij.psi.PsiElement;

/**
 * User: Dmitry.Krasilschikov
 * Date: 22.05.2007
 */
public class GroovyWithTryFinallySurrounder extends GroovyWithTrySurrounder {
  protected String getElementsTemplateAsString(PsiElement[] nodes) {
    return super.getElementsTemplateAsString(nodes) + "finally { handler \n }";
  }

  public String getTemplateDescription() {
    return super.getTemplateDescription() + " / finally";
  }
}
