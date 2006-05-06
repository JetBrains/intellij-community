package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.psi.AntCall;
import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntProject;
import com.intellij.lang.ant.psi.AntTarget;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class AntTargetImpl extends AntStructuredElementImpl implements AntTarget {

  private AntTarget[] myDependsTargets;
  private AntCall[] myCalls;

  public AntTargetImpl(AntElement parent, final XmlTag tag) {
    super(parent, tag);
    myDefinition = getAntProject().getTargetDefinition();
  }

  @NonNls
  public String toString() {
    @NonNls StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append("AntTarget:[");
      builder.append(getName());
      if (getDescription() != null) {
        builder.append(" :");
        builder.append(getDescription());
      }
      builder.append("]");
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
  public String getDescription() {
    return getSourceElement().getAttributeValue("description");
  }

  @NotNull
  public AntTarget[] getDependsTargets() {
    if (myDependsTargets == null) {
      final String depends = getSourceElement().getAttributeValue("depends");
      if (depends == null || depends.length() == 0) {
        myDependsTargets = AntProjectImpl.EMPTY_TARGETS;
      } else {
        AntProject project = (AntProject) getAntParent();
        final List<AntTarget> targets = new ArrayList<AntTarget>();
        for (String name : depends.split(",")) {
          final AntTarget antTarget = project.getTarget(name);
          if (antTarget != null) {
            targets.add(antTarget);
          }
        }
        myDependsTargets = targets.toArray(new AntTarget[targets.size()]);
      }
    }
    return myDependsTargets;
  }

  public void setDependsTargets(@NotNull AntTarget[] targets) {
    myDependsTargets = targets;
  }

  @NotNull
  public AntCall[] getAntCalls() {
    if (myCalls == null) {
      List<AntCall> calls = new ArrayList<AntCall>();
      for (AntElement element : getChildren()) {
        if (element instanceof AntCall) {
          calls.add((AntCall) element);
        }
      }
      myCalls = calls.toArray(new AntCall[calls.size()]);
    }
    return myCalls;
  }
}
