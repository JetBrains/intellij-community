package org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.virtual.DynamicVirtualProperty;

/**
 * User: Dmitry.Krasilschikov
 * Date: 20.12.2007
 */
public class DPropertyElement extends DItemElement {
  public DPropertyElement(DynamicVirtualProperty virtualElement) {
    super(virtualElement);
  }

  public DPropertyElement(DynamicVirtualProperty virtualElement, boolean isSetTypeAndName) {
    super(virtualElement, isSetTypeAndName);
  }

  @NotNull
  public DynamicVirtualProperty getDynamicVirtualElement() {
    return ((DynamicVirtualProperty) super.getDynamicVirtualElement());
  }
}
