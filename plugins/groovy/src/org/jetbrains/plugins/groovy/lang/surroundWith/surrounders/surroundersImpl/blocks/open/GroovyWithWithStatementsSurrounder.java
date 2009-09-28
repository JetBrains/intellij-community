package org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.blocks.open;

/**
 * User: Dmitry.Krasilschikov
 * Date: 25.05.2007
 */
public class GroovyWithWithStatementsSurrounder extends GroovySimpleManyStatementsSurrounder {

  protected String getReplacementTokens() {
    return "with(a){\n}";
  }

  public String getTemplateDescription() {
    return "with () {...}";
  }
}
