package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.AntElementRole;
import com.intellij.lang.ant.psi.*;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.psi.PsiLock;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class AntTargetImpl extends AntStructuredElementImpl implements AntTarget, AntProperty {

  private AntTarget[] myDependsTargets;
  private AntNameElementImpl myPropElement;

  public AntTargetImpl(AntElement parent, final XmlTag tag) {
    super(parent, tag);
    myDefinition = getAntFile().getTargetDefinition();
  }

  @NonNls
  public String toString() {
    @NonNls final StringBuilder builder = StringBuilderSpinAllocator.alloc();
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

  public AntElementRole getRole() {
    return AntElementRole.TARGET_ROLE;
  }

  public void acceptAntElementVisitor(@NotNull final AntElementVisitor visitor) {
    visitor.visitAntTarget(this);
  }

  @NotNull
  public String getQualifiedName() {
    final AntProject project = getAntProject();
    final String projectName = (project != null) ? project.getName() : null;
    final String name = getName();
    final String result = (projectName == null || projectName.length() == 0) ? name : projectName + '.' + name;
    return (result == null) ? "" : result;
  }

  @Nullable
  public String getDescription() {
    return getSourceElement().getAttributeValue(AntFileImpl.DESCRIPTION_ATTR);
  }

  @Nullable
  public String getConditionalPropertyName(final ConditionalAttribute attrib) {
    final XmlAttribute propNameAttribute = getSourceElement().getAttribute(attrib.getXmlName(), null);
    if (propNameAttribute != null) {
      final XmlAttributeValue valueElement = propNameAttribute.getValueElement();
      if (valueElement != null) {
        return computeAttributeValue(valueElement.getValue());
      }
    }
    return null;
  }

  @NotNull
  public AntTarget[] getDependsTargets() {
    synchronized (PsiLock.LOCK) {
      if (myDependsTargets == null) {
        final String depends = getSourceElement().getAttributeValue(AntFileImpl.DEPENDS_ATTR);
        if (depends == null || depends.length() == 0) {
          myDependsTargets = EMPTY_TARGETS;
        }
        else {
          final AntProject project = getAntProject();
          final List<AntTarget> targets = new ArrayList<AntTarget>();
          for (final String name : depends.split(",")) {
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
  }

  public void setDependsTargets(@NotNull AntTarget[] targets) {
    synchronized (PsiLock.LOCK) {
      myDependsTargets = targets;
    }
  }

  public void clearCaches() {
    synchronized (PsiLock.LOCK) {
      super.clearCaches();
      myDependsTargets = null;
      if (myPropElement != null) {
        getAntProject().clearCaches();
        myPropElement = null;
      }
    }
  }

  /**
   * Here comes AntProperty implementation for supporting properties defined directly
   * on the "if" and "unless" attributes of the target.
   */

  @Nullable
  public String getValue(final String propName) {
    return null;
  }

  @Nullable
  public String getFileName() {
    return null;
  }

  @Nullable
  public PropertiesFile getPropertiesFile() {
    return null;
  }

  @Nullable
  public String getPrefix() {
    return null;
  }

  @Nullable
  public String getEnvironment() {
    return null;
  }

  @Nullable
  public String[] getNames() {
    return (myPropElement == null) ? null : new String[]{myPropElement.getName()};
  }

  public boolean isMacroDefined() {
    return false;
  }

  /**
   * Navigation to a property (if, unless)
   */
  @Nullable
  @SuppressWarnings({"HardCodedStringLiteral"})
  public AntElement getFormatElement() {
    return myPropElement;
  }

  /* hack */
  public void setPropertyDefinitionElement(final XmlAttributeValue sourceElement) {
    myPropElement = new AntNameElementImpl(this, sourceElement);
  }

  protected AntElement[] getChildrenInner() {
    synchronized (PsiLock.LOCK) {
      final AntElement[] baseChildren = super.getChildrenInner();
      if (myPropElement == null) {
        return baseChildren;
      }
      if (!myInGettingChildren) {
        myInGettingChildren = true;
        try {
          final List<AntElement> children = new ArrayList<AntElement>(baseChildren.length + 1);
          children.add(myPropElement);
          for (final AntElement child : baseChildren) {
            children.add(child);
          }
          return children.toArray(new AntElement[children.size()]);
        }
        finally {
          myInGettingChildren = false;
        }
      }
      return AntElement.EMPTY_ARRAY;
    }
  }

}
