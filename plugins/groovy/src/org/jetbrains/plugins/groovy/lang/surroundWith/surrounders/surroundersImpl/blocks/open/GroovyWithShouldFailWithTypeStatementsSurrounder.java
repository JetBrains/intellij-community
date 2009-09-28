package org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.blocks.open;

/**
 * Provides the shouldFail() { ... }  surround with. It follows a Template Method pattern. 
 * @author Hamlet D'Arcy
 * @since 03.02.2009
 */
public class GroovyWithShouldFailWithTypeStatementsSurrounder extends GroovySimpleManyStatementsSurrounder {

  protected String getReplacementTokens() {
    return "shouldFail(a){\n}";
  }

  public String getTemplateDescription() {
    return "shouldFail () {...}";
  }
}
