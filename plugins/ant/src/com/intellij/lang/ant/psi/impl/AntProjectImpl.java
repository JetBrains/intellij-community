package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.psi.AntProject;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.Nullable;

public class AntProjectImpl extends AntElementImpl implements AntProject {

  private String myName;
  private String myDefaultTarget;
  private String myBaseDir;

  public AntProjectImpl(final XmlTag tag) {
    super(tag);
  }

  public String toString() {
    return "AntProject: " + getElementName();
  }

  @Nullable
  public String getElementName() {
    parseTag();
    return myName;
  }

  @Nullable
  public String getDefaultTarget() {
    parseTag();
    return myDefaultTarget;
  }

  @Nullable
  public String getBaseDir() {
    parseTag();
    return myBaseDir;
  }

  void parseTag() {
    if (myName == null) {
      final XmlTag tag = getSourceTag();
      final String name = tag.getName();
      if ("project".compareToIgnoreCase(name) == 0) {
        myName = tag.getAttributeValue("name");
        myDefaultTarget = tag.getAttributeValue("default");
        myBaseDir = tag.getAttributeValue("basedir");
      }
    }
  }
}
