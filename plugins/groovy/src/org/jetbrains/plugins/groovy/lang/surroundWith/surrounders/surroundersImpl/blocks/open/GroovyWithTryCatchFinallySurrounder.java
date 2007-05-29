package org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.blocks.open;

import com.intellij.lang.ASTNode;

/**
 * User: Dmitry.Krasilschikov
 * Date: 22.05.2007
 */
public class GroovyWithTryCatchFinallySurrounder extends GroovyWithTryCatchSurrounder {

  public String getTemplateDescription() {
    return super.getTemplateDescription() + " / finally";
  }

  protected String getElementsTemplateAsString(ASTNode[] nodes) {
    return super.getElementsTemplateAsString(nodes) + " finally { handler \n }";
  }
}
