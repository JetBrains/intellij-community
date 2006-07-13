package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.psi.AntCall;
import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntProperty;
import com.intellij.lang.ant.psi.AntTarget;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class AntCallImpl extends AntTaskImpl implements AntCall {

  private AntTarget[] myDependsTargets = null;
  private AntProperty[] myParams = null;

  public AntCallImpl(final AntElement parent, final XmlElement sourceElement, final AntTypeDefinition definition) {
    super(parent, sourceElement, definition);
  }

  public String toString() {
    @NonNls StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append("AntCall to ");
      final AntTarget target = getTarget();
      builder.append((target == null) ? "null" : target.toString());
      return builder.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  @Nullable
  public AntTarget getTarget() {
    final String target = getSourceElement().getAttributeValue("target");
    AntTarget result = getAntProject().getTarget(target);
    if (result != null) {
      result.setDependsTargets(getDependsTargets());
    }
    return result;
  }

  public void setTarget(AntTarget target) throws IncorrectOperationException {
    getSourceElement().setAttribute("target", target.getName());
  }

  @NotNull
  public AntProperty[] getParams() {
    if (myParams == null) {
      final List<AntProperty> properties = new ArrayList<AntProperty>();
      for (AntElement element : getChildren()) {
        if (element instanceof AntProperty) {
          properties.add((AntProperty)element);
        }
      }
      myParams = properties.toArray(new AntProperty[properties.size()]);
    }
    return myParams;
  }

  public void clearCaches() {
    super.clearCaches();
    myDependsTargets = null;
  }

  @NotNull
  private AntTarget[] getDependsTargets() {
    if (myDependsTargets == null) {
      final List<AntTarget> targets = new ArrayList<AntTarget>();
      for (AntElement element : getChildren()) {
        if (element instanceof AntTarget) {
          targets.add((AntTarget)element);
        }
      }
      myDependsTargets = targets.toArray(new AntTarget[targets.size()]);
    }
    return myDependsTargets;
  }
}
