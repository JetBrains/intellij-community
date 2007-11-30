package org.jetbrains.plugins.groovy.annotator.intentions.dynamic.properties.elements;

/**
 * User: Dmitry.Krasilschikov
 * Date: 30.11.2007
 */
public class DynamicPropertyModuleElement extends DynamicElement {
  public DynamicPropertyModuleElement(String moduleName) {
    super(MODULE_TAG_NAME);
    addContent(moduleName);
  }
}
