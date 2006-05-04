package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntStructuredElement;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.lang.ant.psi.introspection.AntTypeId;
import com.intellij.lang.ant.psi.introspection.impl.AntTypeDefinitionImpl;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AntStructuredElementImpl extends AntElementImpl implements AntStructuredElement {
  protected AntTypeDefinition myDefinition;
  protected boolean myDefinitionCloned = false;
  private AntElement myIdElement;
  private AntElement myNameElement;
  private Map<String, AntElement> myReferencedElements = null;

  public AntStructuredElementImpl(final AntElement parent, final XmlElement sourceElement) {
    super(parent, sourceElement);
    final String id = getId();
    if (id != null && parent instanceof AntStructuredElement) {
      myIdElement = new AntElementImpl(this, getSourceElement().getAttribute("id", null));
      AntStructuredElement se = (AntStructuredElement) parent;
      se.registerRefId(id, myIdElement);
    }
    XmlAttribute nameAttr = getSourceElement().getAttribute("name", null);
    if (nameAttr != null) {
      myNameElement = new AntElementImpl(this, nameAttr);
    }
  }

  public AntStructuredElementImpl(final AntElement parent, final XmlElement sourceElement, final AntTypeDefinition definition) {
    this(parent, sourceElement);
    myDefinition = definition;
    final AntTypeId id = new AntTypeId(getSourceElement().getName(), getSourceElement().getNamespace());
    if (definition != null && !definition.getTypeId().equals(id)) {
      myDefinition = new AntTypeDefinitionImpl((AntTypeDefinitionImpl) myDefinition);
      myDefinition.setTypeId(id);
      myDefinitionCloned = true;
    }
  }

  @NotNull
  public XmlTag getSourceElement() {
    return (XmlTag) super.getSourceElement();
  }

  public String toString() {
    @NonNls StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append("AntStructuredElement[");
      builder.append(getSourceElement().getName());
      builder.append("]");
      return builder.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  public String getName() {
    return (myNameElement == null) ? super.getName() : ((XmlAttribute) myNameElement.getSourceElement()).getValue();
  }

  public PsiElement setName(String name) throws IncorrectOperationException {
    if (myNameElement == null) {
      return super.setName(name);
    }
    ((XmlAttribute) myNameElement.getSourceElement()).setValue(name);
    subtreeChanged();
    return this;
  }

  protected AntElement[] getChildrenInner() {
    final List<AntElement> children = new ArrayList<AntElement>();
    if (myIdElement != null) {
      children.add(myIdElement);
    }
    if (myNameElement != null) {
      children.add(myNameElement);
    }
    for (final XmlTag tag : getSourceElement().getSubTags()) {
      children.add(AntElementFactory.createAntElement(this, tag));
    }
    return children.toArray(new AntElement[children.size()]);
  }

  public AntTypeDefinition getTypeDefinition() {
    return myDefinition;
  }

  public void registerCustomType(final AntTypeDefinition def) {
    if (myDefinition != null) {
      if (!myDefinitionCloned) {
        myDefinition = new AntTypeDefinitionImpl((AntTypeDefinitionImpl) myDefinition);
        myDefinitionCloned = true;
      }
      myDefinition.registerNestedType(def.getTypeId(), def.getClassName());
      getAntProject().registerCustomType(def);
    }
  }

  @Nullable
  public String getId() {
    return getSourceElement().getAttributeValue("id");
  }

  public void registerRefId(final String id, AntElement element) {
    if (myReferencedElements == null) {
      myReferencedElements = new HashMap<String, AntElement>();
    }
    myReferencedElements.put(id, element);
  }

  public AntElement getElementByRefId(final String refid) {
    AntElement parent = this;
    while (true) {
      parent = parent.getAntParent();
      if (parent == null) {
        return null;
      }
      if (parent instanceof AntStructuredElement) {
        AntStructuredElementImpl se = (AntStructuredElementImpl) parent;
        if (se.myReferencedElements != null) {
          final AntElement refse = se.myReferencedElements.get(refid);
          if (refse != null) {
            return refse;
          }
        }
      }
    }
  }

  @NotNull
  public String[] getRefIds() {
    if (myReferencedElements == null) {
      return new String[0];
    }
    return myReferencedElements.keySet().toArray(new String[myReferencedElements.size()]);
  }

  public void clearCaches() {
    super.clearCaches();
    myReferencedElements = null;
  }

  public PsiElement getNavigationElement() {
    return (myNameElement == null) ? this : myNameElement;
  }
}
