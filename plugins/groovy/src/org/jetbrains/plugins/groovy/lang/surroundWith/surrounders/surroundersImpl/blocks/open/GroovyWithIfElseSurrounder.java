package org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.blocks.open;

import com.intellij.psi.PsiElement;

/**
 * User: Dmitry.Krasilschikov
 * Date: 25.05.2007
 */
public class GroovyWithIfElseSurrounder extends GroovyWithIfSurrounder {
  @Override
  protected String getElementsTemplateAsString(PsiElement[] elements) {
    return super.getElementsTemplateAsString(elements) + " else { \n }";
  }

  @Override
  public String getTemplateDescription() {
    return super.getTemplateDescription() + " / else";
  }
}
