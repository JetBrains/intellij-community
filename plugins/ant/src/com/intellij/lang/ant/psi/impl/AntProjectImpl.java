package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.psi.AntFile;
import com.intellij.lang.ant.psi.AntProject;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.Nullable;

public class AntProjectImpl extends AntElementImpl implements AntProject {

  private String myName;
  private String myDefaultTarget;
  private String myBaseDir;

  public AntProjectImpl(final XmlFile xmlFile, final AntFile file) {
    super(xmlFile, file);
    parseXml();
  }

  @Nullable
  public String getName() {
    return myName;
  }

  @Nullable
  public String getDefaultTarget() {
    return myDefaultTarget;
  }

  @Nullable
  public String getBaseDir() {
    return myBaseDir;
  }

  private void parseXml() {
    final XmlDocument xmlDoc = ((XmlFile)getSourceElement()).getDocument();
    final XmlTag tag = xmlDoc.getRootTag();
    parseTag(tag);
  }

  void parseTag(XmlTag tag) {
    final String name = tag.getName();
    if ("project".compareToIgnoreCase(name) == 0) {
      myName = tag.getAttributeValue("name");
      myDefaultTarget = tag.getAttributeValue("default");
      myBaseDir = tag.getAttributeValue("basedir");
    }
    else if ("target".compareToIgnoreCase(name) == 0) {
      final AntTargetImpl antTarget = new AntTargetImpl(tag, myFile);
      //add(antTarget);
      antTarget.parseTag(tag);
    }
    for (XmlTag subTag : tag.getSubTags()) {
      parseTag(subTag);
    }
  }
}
