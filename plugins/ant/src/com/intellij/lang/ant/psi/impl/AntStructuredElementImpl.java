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
  private boolean myDefinitionCloned = false;
  private AntElement myIdElement;
  private AntElement myNameElement;
  private Map<String, AntElement> myReferencedElements = null;

  public AntStructuredElementImpl(final AntElement parent, final XmlElement sourceElement) {
    super(parent, sourceElement);
    if (parent instanceof AntStructuredElement) {
      final XmlAttribute idAttr = getSourceElement().getAttribute("id", null);
      if (idAttr != null) {
        AntStructuredElement se = (AntStructuredElement) parent;
        myIdElement = new AntNameElementImpl(this, idAttr.getValueElement());
        se.registerRefId(myIdElement.getName(), this);
      }
    }
    XmlAttribute nameAttr = getSourceElement().getAttribute("name", null);
    if (nameAttr != null) {
      myNameElement = new AntNameElementImpl(this, nameAttr.getValueElement());
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
    if (myNameElement != null) {
      return myNameElement.getName();
    }
    if (myIdElement != null) {
      return myIdElement.getName();
    }
    return super.getName();
  }

  public PsiElement setName(String name) throws IncorrectOperationException {
    if (myNameElement != null) {
      myNameElement.setName(name);
      subtreeChanged();
    } else if (myIdElement != null) {
      myIdElement.setName(name);
      subtreeChanged();
    } else {
      super.setName(name);
    }
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

  public int getTextOffset() {
    if(myNameElement != null) {
      return myNameElement.getTextOffset();
    }
    if( myIdElement != null ) {
      return myIdElement.getTextOffset();
    }
    return super.getTextOffset();
  }
}
