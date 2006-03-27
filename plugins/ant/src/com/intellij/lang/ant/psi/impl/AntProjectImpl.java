package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntProject;
import com.intellij.lang.ant.psi.AntTarget;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.impl.source.resolve.reference.impl.GenericReference;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class AntProjectImpl extends AntElementImpl implements AntProject {
  final static AntTarget[] EMPTY_TARGETS = new AntTarget[0];

  private AntTarget[] myTargets;

  public AntProjectImpl(final AntFileImpl parent, final XmlTag tag) {
    super(parent, tag);
  }

  @NonNls
  public String toString() {
    @NonNls StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append("AntProject: ");
      builder.append(getName());
      if (getDescription() != null) {
        builder.append(" [");
        builder.append(getDescription());
        builder.append("]");
      }
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

  @Nullable
  public String getName() {
    return getSourceElement().getAttributeValue("name");
  }

  public PsiElement setName(String name) throws IncorrectOperationException {
    getSourceElement().setAttribute("name", name);
    subtreeChanged();
    return this;
  }

  @Nullable
  public String getBaseDir() {
    return getSourceElement().getAttributeValue("basedir");
  }

  @Nullable
  public String getDescription() {
    final XmlTag tag = getSourceElement().findFirstSubTag("description");
    return tag != null ? tag.getValue().getTrimmedText() : null;
  }

  @NotNull
  public AntTarget[] getTargets() {
    if (myTargets != null) return myTargets;
    final List<AntTarget> targets = new ArrayList<AntTarget>();
    for (final AntElement child : getChildren()) {
      if (child instanceof AntTarget)
        targets.add((AntTarget)child);
    }
    return myTargets = targets.toArray(new AntTarget[targets.size()]);
  }

  @Nullable
  public AntTarget getDefaultTarget() {
    final PsiReference[] references = getReferences();
    for (PsiReference ref : references) {
      final GenericReference reference = (GenericReference)ref;
      if (reference.getType().isAssignableTo(ReferenceType.ANT_TARGET)) {
        return (AntTarget)reference.resolve();
      }
    }
    return null;
  }

  @Nullable
  public AntTarget getTarget(final String name) {
    AntTarget[] targets = getTargets();
    for (AntTarget target : targets) {
      if (name.equals(target.getName())) {
        return target;
      }
    }
    return null;
  }

  @SuppressWarnings("HardCodedStringLiteral")
  protected AntElement[] getChildrenInner() {
    final XmlTag[] tags = getSourceElement().getSubTags();
    final List<AntElement> children = new ArrayList<AntElement>();
    for (final XmlTag tag : tags) {
      final String tagName = tag.getName();
      if ("target".equals(tagName)) {
        children.add(new AntTargetImpl(this, tag));
      }
      else if("property".equals(tagName)) {
        children.add(new AntPropertyImpl(this, tag));
      }
    }
    return children.toArray(new AntElement[children.size()]);
  }
}
