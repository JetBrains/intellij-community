package org.jetbrains.plugins.groovy.annotator.intentions.dynamic;

/**
 * User: Dmitry.Krasilschikov
 * Date: 04.03.2008
 */
public interface DNamedElement extends DElement{
  public String getName();

  public void setName(String name);
}
