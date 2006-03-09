package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntFile;
import com.intellij.lang.ant.psi.AntProject;
import com.intellij.lang.ant.psi.AntTarget;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public class AntProjectImpl extends AntElementImpl implements AntProject {

  final static AntTarget[] EMPTY_TARGETS = new AntTarget[0];

  private String myName;
  private String myDefaultTargetName;
  private String myBaseDir;
  private String myDescription;
  private AntTarget[] myTargets;
  private AntTarget myDefaultTarget;

  public AntProjectImpl(final AntFile parent, final XmlTag tag) {
    super(parent, tag);
  }

  @NonNls
  public String toString() {
    @NonNls StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append("AntProject: ");
      builder.append(getName());
      if (myDescription != null) {
        builder.append(" [");
        builder.append(myDescription);
        builder.append("]");
      }
      return builder.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  @Nullable
  public String getName() {
    parseTag();
    return myName;
  }

  @Nullable
  public String getBaseDir() {
    parseTag();
    return myBaseDir;
  }

  @Nullable
  public String getDescription() {
    parseTag();
    return myDescription;
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
        if (defaultTarget.equals(target.getName())) {
          myDefaultTarget = target;
          break;
        }
      }
    }
    return myDefaultTarget;
  }

  @Nullable
  public AntTarget getTarget(final String name) {
    AntTarget[] targets = getAllTargets();
    for (AntTarget target : targets) {
      if (name.compareToIgnoreCase(target.getName()) == 0) {
        return target;
      }
    }
    return null;
  }

  @SuppressWarnings("HardCodedStringLiteral")
  protected AntElement parseSubTag(final XmlTag tag) {
    if ("target".equalsIgnoreCase(tag.getName())) {
      return new AntTargetImpl(this, tag);
    }
    else if ("property".equalsIgnoreCase(tag.getName())) {
      return new AntPropertySetImpl(this, tag);
    }
    return null;
  }

  @SuppressWarnings("HardCodedStringLiteral")
  private void parseTag() {
    if (myName == null) {
      final XmlTag tag = getSourceTag();
      final String name = tag.getName();
      if ("project".equalsIgnoreCase(name)) {
        myName = tag.getAttributeValue("name");
        myDefaultTargetName = tag.getAttributeValue("default");
        myBaseDir = tag.getAttributeValue("basedir");
        final XmlTag descTag = tag.findFirstSubTag("description");
        if (descTag != null) {
          myDescription = descTag.getValue().getText();
        }
      }
    }
  }
}
