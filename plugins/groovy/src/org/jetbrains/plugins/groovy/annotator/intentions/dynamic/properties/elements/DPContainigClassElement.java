package org.jetbrains.plugins.groovy.annotator.intentions.dynamic.properties.elements;

import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.properties.DynamicProperty;

/**
 * User: Dmitry.Krasilschikov
 * Date: 20.12.2007
 */
public class DPContainigClassElement extends DPElement{
  public DPContainigClassElement(String typeQualName) {
    super(CONTAINIG_CLASS_TAG);
    setAttribute(CONTAINIG_CLASS_TYPE_ATTRIBUTE, typeQualName);
  }

  public DPContainigClassElement(DynamicProperty dynamicProperty) {
    this(dynamicProperty.getContainingClassQualifiedName());
    setContent(new DPPropertyElement(dynamicProperty));
  }
}
