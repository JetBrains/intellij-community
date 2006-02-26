package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.psi.AntFile;
import com.intellij.lang.ant.psi.AntTarget;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.Nullable;

public class AntTargetImpl extends AntElementImpl implements AntTarget {

  private String myName;
  private String myDepends;

  public AntTargetImpl(final XmlTag tag, final AntFile file) {
    super(tag, file);
    parseTag(tag);
  }

  @Nullable
  public String getName() {
    return myName;
  }

  void parseTag(XmlTag tag) {
    final String name = tag.getName();
    if ("target".compareToIgnoreCase(name) == 0) {
      myName = tag.getAttributeValue("name");
      myDepends = tag.getAttributeValue("depends");
    }
    for (XmlTag subTag : tag.getSubTags()) {
      parseTag(subTag);
    }
  }
}
