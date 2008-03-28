package org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements;

import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements.DElement;

/**
 * User: Dmitry.Krasilschikov
 * Date: 04.03.2008
 */
public interface DTypedElement extends DElement {
  public String getType();

  public void setType(String type);
}
