package org.jetbrains.plugins.groovy.annotator.intentions.dynamic.properties.elements;

import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.properties.real.DynamicPropertyReal;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.properties.virtual.DynamicPropertyVirtual;

/**
 * User: Dmitry.Krasilschikov
 * Date: 20.12.2007
 */
public class DPContainingClassElement extends DPElement{
  public DPContainingClassElement(String typeQualName) {
    super(CONTAINIG_CLASS_TAG);
    setAttribute(CONTAINIG_CLASS_TYPE_ATTRIBUTE, typeQualName);
  }

  public DPContainingClassElement(DynamicPropertyVirtual dynamicPropertyVirtual) {
    this(dynamicPropertyVirtual.getContainingClassQualifiedName());
    setContent(new DPPropertyElement(dynamicPropertyVirtual));
  }

  public String getContainingClassName(){
    return getAttributeValue(CONTAINIG_CLASS_TYPE_ATTRIBUTE);
  }
}
