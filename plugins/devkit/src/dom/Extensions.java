// Generated on Wed Nov 07 17:26:02 MSK 2007
// DTD/Schema  :    plugin.dtd

package org.jetbrains.idea.devkit.dom;

import com.intellij.util.xml.Attribute;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericAttributeValue;
import org.jetbrains.annotations.NotNull;

public interface Extensions extends DomElement {
  @NotNull
  @Attribute("defaultExtensionNs")
  GenericAttributeValue<String> getDefaultExtensionNs();
  @Attribute("defaultExtensionNs")
  void setDefaultExtensionNs(String ns);
}
