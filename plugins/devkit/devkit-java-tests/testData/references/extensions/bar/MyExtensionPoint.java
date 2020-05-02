package bar;

import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;

/**
 * MyExtensionPoint JavaDoc.
 */
public class MyExtensionPoint {

  @RequiredElement
  @Attribute
  public String implementationClass;

  @Tag
  public Integer intValue;
}