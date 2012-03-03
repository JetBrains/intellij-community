package org.jetbrains.android.dom.xml;

import com.intellij.util.xml.Required;
import org.jetbrains.android.dom.AndroidAttributeValue;

/**
 * @author Eugene.Kudelevsky
 */
public interface Extra extends XmlResourceElement {
  @Required
  AndroidAttributeValue<String> getName();

  @Required
  AndroidAttributeValue<String> getValue();
}
