package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.config.AntDefaultIntrospector;
import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntTask;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class AntTaskImpl extends AntElementImpl implements AntTask {

  private static final String[] EMPTY_ATTR_LIST = new String[0];
  private String[] myAttributeNames;
  private String[] myNestedElements;

  public AntTaskImpl(final AntElement parent, final XmlElement sourceElement) {
    super(parent, sourceElement);
  }

  @NotNull
  public XmlTag getSourceElement() {
    return (XmlTag)super.getSourceElement();
  }

  public String getName() {
    return getSourceElement().getName();
  }

  public String[] getAttributeNames() {
    if (myAttributeNames == null) {
      final Map attributes = AntDefaultIntrospector.getTaskAttributes(getName());
      if (attributes == null) {
        myAttributeNames = EMPTY_ATTR_LIST;
      }
      else {
        myAttributeNames = (String[])attributes.keySet().toArray(new String[attributes.size()]);
      }
    }
    return myAttributeNames;
  }

  public Class getAttributeType(final String attributeName) {
    return AntDefaultIntrospector.getTaskClass(getName());
  }

  public String[] getNestedElements() {
    if (myNestedElements == null) {
      final Map nestedElements = AntDefaultIntrospector.getTaskNestedElements(getName());
      if (nestedElements == null) {
        myNestedElements = EMPTY_ATTR_LIST;
      }
      else {
        myNestedElements = (String[])nestedElements.keySet().toArray(new String[nestedElements.size()]);
      }
    }
    return myAttributeNames;
  }
}
