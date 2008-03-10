package org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements;

/**
 * User: Dmitry.Krasilschikov
 * Date: 20.12.2007
 */
public class DPropertyElement extends DItemElement {
  //Do not use directly! Persistance component uses defualt constructor for deserializable
  public DPropertyElement() {
    super(null, null);
  }

  public DPropertyElement(String name, String type) {
    super(name, type);
  }
}
