package org.jetbrains.idea.devkit.dom;

import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.NameValue;

/**
 * @author mike
 */
public interface Extension extends DomElement {

  @NameValue
  GenericAttributeValue<String> getId();

  GenericAttributeValue<String> getOrder();

}
