package org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DElement;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DynamicVirtualElement;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.virtual.DynamicVirtualMethod;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.virtual.DynamicVirtualProperty;

/**
 * User: Dmitry.Krasilschikov
 * Date: 13.02.2008
 */

/*
 * Base class for Dynamic property and method
 */

public class DItemElement extends DElement {
  @NotNull
  private DynamicVirtualElement myDynamicVirtualElement;
  private String myHightlightedText;

  public DItemElement(DynamicVirtualElement virtualElement) {
    this(virtualElement, true);
  }

  public DItemElement(DynamicVirtualElement virtualElement, boolean isSetTypeAndName) {
    super(virtualElement instanceof DynamicVirtualMethod ? METHOD_TAG :
        (virtualElement instanceof DynamicVirtualProperty) ? PROPERTY_TAG : ERROR_TAG);

    myDynamicVirtualElement = virtualElement;

    if (isSetTypeAndName) {
      setAttribute(TYPE_ATTRIBUTE, virtualElement.getType());
      setAttribute(NAME_ATTRIBUTE, virtualElement.getName());
    }
  }

  @NotNull
  public DynamicVirtualElement getDynamicVirtualElement() {
    return myDynamicVirtualElement;
  }

  public void setDynamicVirtualElement(@NotNull DynamicVirtualElement dynamicVirtualElement) {
    myDynamicVirtualElement = dynamicVirtualElement;
  }

  public String getHightlightedText() {
    return myHightlightedText;
  }

  public void setHightlightedText(String hightlightedText) {
    myHightlightedText = hightlightedText;
  }
}