package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.psi.AntCall;
import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntProject;
import com.intellij.lang.ant.psi.AntTarget;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class AntCallImpl extends AntElementImpl implements AntCall {

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
    return (project == null || target == null) ? null : project.getTarget(target);
  }

  public void setTarget(AntTarget target) throws IncorrectOperationException {
    getSourceElement().setAttribute("target", target.getName());
    subtreeChanged();
  }
}
