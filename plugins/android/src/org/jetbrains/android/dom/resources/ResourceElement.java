package org.jetbrains.android.dom.resources;

import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.Required;
import org.jetbrains.android.dom.AndroidDomElement;

/**
 * @author yole
 */
public interface ResourceElement extends AndroidDomElement {
  @Required
  GenericAttributeValue<String> getName();

  String getValue();

  void setValue(String s);
}
