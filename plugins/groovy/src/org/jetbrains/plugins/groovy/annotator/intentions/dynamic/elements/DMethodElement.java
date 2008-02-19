package org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements;

import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DynamicVirtualElement;

/**
 * User: Dmitry.Krasilschikov
 * Date: 12.02.2008
 */
public class DMethodElement extends DItemElement {
  DParameterElement[] myParametersElements;

  public DMethodElement(DynamicVirtualElement virtualMethod) {
    super(virtualMethod);
  }

  public DParameterElement[] getParametersElements() {
    return myParametersElements;
  }

  public void setParametersElements(DParameterElement[] parametersElements) {
    myParametersElements = parametersElements;
  }
}