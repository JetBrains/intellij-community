package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.config.AntDefaultIntrospector;
import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntTask;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class AntTaskImpl extends AntElementImpl implements AntTask {

  private static final String[] EMPTY_ATTR_LIST = new String[0];
  private String[] myAttributeNames;
  private String[] myNestedElements;

  public AntTaskImpl(final AntElement parent, final XmlElement sourceElement) {
    super(parent, sourceElement);
  }

  public String toString() {
    @NonNls StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append("AntTask[");
      builder.append(getName());
      builder.append("]");
      return builder.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  @NotNull
  public XmlTag getSourceElement() {
    return (XmlTag)super.getSourceElement();
  }

  public String getName() {
    return getSourceElement().getName();
  }

  public PsiElement setName(String name) throws IncorrectOperationException {
    throw new IncorrectOperationException();
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

  public Class getAttributeType(final String attr) {
    return AntDefaultIntrospector.getTaskAttributeType(getName(), attr);
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

  public Class getNestedElementType(final String element) {
    return AntDefaultIntrospector.getTaskNestedElementType(getName(), element);
  }
}
