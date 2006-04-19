package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntTaskDef;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.psi.xml.XmlElement;

public class AntTaskDefImpl extends AntTaskImpl implements AntTaskDef {

  public AntTaskDefImpl(final AntElement parent, final XmlElement sourceElement, final AntTypeDefinition definition) {
    super(parent, sourceElement, definition);
  }

  public String getDefinedName() {
    return getSourceElement().getAttributeValue("name");
  }

  public String getClassName() {
    return getSourceElement().getAttributeValue("classname");
  }

  public String getClassPath() {
    return getSourceElement().getAttributeValue("classpath");
  }
}
