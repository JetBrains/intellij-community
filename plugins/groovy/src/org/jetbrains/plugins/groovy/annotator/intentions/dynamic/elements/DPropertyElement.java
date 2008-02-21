package org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements;

import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DynamicVirtualElement;

/**
 * User: Dmitry.Krasilschikov
 * Date: 20.12.2007
 */
public class DPropertyElement extends DItemElement {
  public DPropertyElement(DynamicVirtualElement virtualElement) {
    super(virtualElement);
  }

  public DPropertyElement(DynamicVirtualElement virtualElement, boolean isSetTypeAndName) {
    super(virtualElement, isSetTypeAndName);
  }
}
