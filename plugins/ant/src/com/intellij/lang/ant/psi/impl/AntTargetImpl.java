package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.psi.AntTarget;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.Nullable;

public class AntTargetImpl extends AntElementImpl implements AntTarget {

  private String myName;
  private String myDepends;

  public AntTargetImpl(final XmlTag tag) {
    super(tag);
  }

  @Nullable
  public String getElementName() {
    return myName;
  }

  void parseTag() {
    if (myName == null) {
      final XmlTag tag = getSourceTag();
      final String name = tag.getName();
      if ("target".compareToIgnoreCase(name) == 0) {
        myName = tag.getAttributeValue("name");
        myDepends = tag.getAttributeValue("depends");
      }
    }
  }
}
