package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.psi.AntFile;
import com.intellij.lang.ant.psi.AntProject;
import com.intellij.lang.ant.psi.AntTarget;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public class AntProjectImpl extends AntElementImpl implements AntProject {

  private final static AntTarget[] EMPTY_TARGETS = new AntTarget[0];

  private String myName;
  private String myDefaultTargetName;
  private String myBaseDir;
  private AntTarget[] myTargets;
  private AntTarget myDefaultTarget;

  public AntProjectImpl(final AntFile parent, final XmlTag tag) {
    super(parent, tag);
  }

  public String toString() {
    return "AntProject: " + getElementName();
  }

  @Nullable
  public String getElementName() {
    parseTag();
    return myName;
  }

  @NotNull
  public PsiElement[] getChildren() {
    if (myChildren == null) {
      ArrayList<PsiElement> children = null;
      final XmlTag tag = getSourceTag();
      final XmlTag[] tags = tag.getSubTags();
      for (XmlTag subtag : tags) {
        PsiElement child = null;
        if ("target".equalsIgnoreCase(subtag.getName())) {
          child = new AntTargetImpl(this, subtag);
        }
        if (child != null) {
          if (children == null) {
            children = new ArrayList<PsiElement>();
          }
          children.add(child);
        }
      }
      if (children != null) {
        myChildren = children.toArray(new PsiElement[children.size()]);
      }
    }
    return super.getChildren();
  }

  @NotNull
  public AntTarget[] getAllTargets() {
    if (myTargets == null) {
      myTargets = EMPTY_TARGETS;
      final PsiElement[] children = getChildren();
      if (children.length > 0) {
        ArrayList<AntTarget> targets = null;
        for (PsiElement child : children) {
          if (child instanceof AntTarget) {
            if (targets == null) {
              targets = new ArrayList<AntTarget>();
            }
            targets.add((AntTarget)child);
          }
        }
        if (targets != null) {
          myTargets = targets.toArray(new AntTarget[ targets.size()]);
        }
      }
    }
    return myTargets;
  }

  @Nullable
  public AntTarget getDefaultTarget() {
    if (myDefaultTarget == null) {
      parseTag();
      final String defaultTarget = myDefaultTargetName;
      if (defaultTarget == null || defaultTarget.length() > 0) {
        return null;
      }
      for (AntTarget target : getAllTargets()) {
        if (defaultTarget.equals(target.getElementName())) {
          myDefaultTarget = target;
          break;
        }
      }
    }
    return myDefaultTarget;
  }

  @Nullable
  public String getBaseDir() {
    parseTag();
    return myBaseDir;
  }

  private void parseTag() {
    if (myName == null) {
      final XmlTag tag = getSourceTag();
      final String name = tag.getName();
      if ("project".compareToIgnoreCase(name) == 0) {
        myName = tag.getAttributeValue("name");
        myDefaultTargetName = tag.getAttributeValue("default");
        myBaseDir = tag.getAttributeValue("basedir");
      }
    }
  }
}
