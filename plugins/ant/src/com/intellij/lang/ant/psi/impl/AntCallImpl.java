package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.psi.*;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class AntCallImpl extends AntElementImpl implements AntCall {

  private AntTarget[] myDependsTargets = null;
  private AntProperty[] myParams = null;

  public AntCallImpl(final AntElement parent, final XmlElement sourceElement) {
    super(parent, sourceElement);
  }

  public String toString() {
    @NonNls StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append("AntCall to ");
      builder.append(getTarget().toString());
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

  public AntTarget getTarget() {
    final String target = getSourceElement().getAttributeValue("target");
    final AntProject project = getAntProject();
    if (project != null) {
      AntTarget result = project.getTarget(target);
      if (result != null) {
        result.setDependsTargets(getDependsTargets());
      }
      return result;
    }
    return null;
  }

  public void setTarget(AntTarget target) throws IncorrectOperationException {
    getSourceElement().setAttribute("target", target.getName());
    subtreeChanged();
  }

  @NotNull
  public AntProperty[] getParams() {
    if(myParams == null) {
      List<AntProperty> properties = new ArrayList<AntProperty>();
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
    myDependsTargets = null;
  }

  @NotNull
  private AntTarget[] getDependsTargets() {
    if (myDependsTargets == null) {
      List<AntTarget> targets = new ArrayList<AntTarget>();
      for (AntElement element : getChildren()) {
        if (element instanceof AntTarget) {
          targets.add((AntTarget)element);
        }
      }
      myDependsTargets = targets.toArray(new AntTarget[targets.size()]);
    }
    return myDependsTargets;
  }

  protected AntElement[] getChildrenInner() {
    final XmlTag[] tags = getSourceElement().getSubTags();
    final List<AntElement> children = new ArrayList<AntElement>();
    for (final XmlTag tag : tags) {
      @NonNls final String tagName = tag.getName();
      if ("target".equals(tagName)) {
        children.add(new AntTargetImpl(this, tag));
      }
      else if("param".equals(tagName)) {
        children.add(new AntPropertyImpl(this, tag));
      }
    }
    return children.toArray(new AntElement[children.size()]);
  }
}
