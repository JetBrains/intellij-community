package org.jetbrains.plugins.groovy.extensions;

import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.xml.Required;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Property;

/**
 * @author Sergey Evdokimov
 */
public class GroovyClassDescriptor extends AbstractExtensionPointBean {

  public static final ExtensionPointName<GroovyClassDescriptor> EP_NAME = new ExtensionPointName<GroovyClassDescriptor>("org.intellij.groovy.classDescriptor");

  @Attribute("class")
  @Required
  public String className;

  @Property(surroundWithTag = false)
  @AbstractCollection(surroundWithTag = false)
  public GroovyMethodDescriptorTag[] methods;

}
