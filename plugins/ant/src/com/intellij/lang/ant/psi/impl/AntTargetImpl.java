package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntProject;
import com.intellij.lang.ant.psi.AntTarget;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public class AntTargetImpl extends AntElementImpl implements AntTarget {

  private String myName;
  private String myDepends;
  private String myDescription;
  private AntTarget[] myDependsTargets;

  public AntTargetImpl(AntProject parent, final XmlTag tag) {
    super(parent, tag);
  }

  @NonNls
  public String toString() {
    @NonNls StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append("AntTarget: ");
      builder.append(getName());
      if (myDescription != null) {
        builder.append(" [");
        builder.append(myDescription);
        builder.append(']');
      }
      final AntTarget[] targets = getDependsTargets();
      if (targets.length > 0) {
        builder.append(" -> [");
        for (AntTarget target : targets) {
          builder.append(' ');
          builder.append(target.getName());
        }
        builder.append(" ]");
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
  public String getDescription() {
    parseTag();
    return myDescription;
  }

  @NotNull
  public AntTarget[] getDependsTargets() {
    if (myDependsTargets == null) {
      myDependsTargets = AntProjectImpl.EMPTY_TARGETS;
      final String depends = myDepends;
      if (depends != null && depends.length() > 0) {
        AntProject project = (AntProject)getParent();
        final String[] names = depends.split(",");
        ArrayList<AntTarget> targets = null;
        for (String name : names) {
          final AntTarget antTarget = project.getTarget(name);
          if (antTarget != null) {
            if (targets == null) {
              targets = new ArrayList<AntTarget>();
            }
            targets.add(antTarget);
          }
        }
        if (targets != null) {
          myDependsTargets = targets.toArray(new AntTarget[targets.size()]);
        }
      }
    }
    return myDependsTargets;
  }

  protected AntElement parseSubTag(final XmlTag tag) {
    return null;
  }

  @SuppressWarnings("HardCodedStringLiteral")
  private void parseTag() {
    if (myName == null) {
      final XmlTag tag = getSourceTag();
      final String name = tag.getName();
      if ("target".equalsIgnoreCase(name)) {
        myName = tag.getAttributeValue("name");
        myDepends = tag.getAttributeValue("depends");
        myDescription = tag.getAttributeValue("description");
      }
    }
  }
}
