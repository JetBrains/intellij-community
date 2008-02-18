package org.jetbrains.plugins.groovy.annotator.intentions.dynamic;

import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements.DItemElement;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements.DMethodElement;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements.DPropertyElement;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.virtual.DynamicVirtualMethod;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.virtual.DynamicVirtualProperty;

/**
 * User: Dmitry.Krasilschikov
 * Date: 20.12.2007
 */
public class DContainingClassElement extends DElement {
  private final String myTypeQualName;

  public DContainingClassElement(String typeQualName) {
    super(CONTAINING_CLASS_TAG);
    myTypeQualName = typeQualName;
    setAttribute(CONTAINIG_CLASS_TYPE_ATTRIBUTE, typeQualName);
  }


  public void addVirtualElement(DynamicVirtualElement virtualElement) {
//    final DynamicVirtualElement.ElementType itemType = virtualElement.getItemType();
//
//    switch (itemType) {
//      case METHOD: {
//        setContent(new DMethodElement(virtualElement.getName()));
//      }
//
//       case PROPERTY: {
//        setContent(new DPropertyElement(virtualElement.getName()));
//      }
//    }
    if (virtualElement instanceof DynamicVirtualMethod) {
      setContent(new DMethodElement(((DynamicVirtualMethod) virtualElement)));
    } else if (virtualElement instanceof DynamicVirtualProperty) {
      setContent(new DPropertyElement(((DynamicVirtualProperty) virtualElement)));
    }
  }

  public void addDynamicItem(DItemElement itemElement) {
    addContent(itemElement);
  }

  public String getContainingClassName() {
    return myTypeQualName;
  }
}
