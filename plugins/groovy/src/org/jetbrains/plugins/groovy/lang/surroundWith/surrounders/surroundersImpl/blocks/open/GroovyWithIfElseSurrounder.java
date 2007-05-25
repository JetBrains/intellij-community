package org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.blocks.open;

import com.intellij.lang.ASTNode;

/**
 * User: Dmitry.Krasilschikov
 * Date: 25.05.2007
 */
public class GroovyWithIfElseSurrounder extends GroovyWithIfSurrounder {
  @Override
  protected String getElementsTemplateAsString(ASTNode[] nodes) {
    return super.getElementsTemplateAsString(nodes) + " else { \n }";
  }

  @Override
  public String getTemplateDescription() {
    return super.getTemplateDescription() + " else {...}";
  }
}
