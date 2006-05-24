package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntStructuredElement;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.lang.ant.psi.introspection.AntTypeId;
import com.intellij.lang.ant.psi.introspection.impl.AntTypeDefinitionImpl;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AntStructuredElementImpl extends AntElementImpl implements AntStructuredElement {
  protected AntTypeDefinition myDefinition;
  private boolean myDefinitionCloned = false;
  private AntElement myIdElement;
  private AntElement myNameElement;
  @NonNls private String myNameElementAttribute;
  private Map<String, AntElement> myReferencedElements = null;
  private int myLastFoundElementOffset = -1;
  private AntElement myLastFoundElement;

  public AntStructuredElementImpl(final AntElement parent,
                                  final XmlElement sourceElement,
                                  @NonNls final String nameElementAttribute) {
    super(parent, sourceElement);
    myNameElementAttribute = nameElementAttribute;
    getIdElement();
    getNameElement();
  }

  public AntStructuredElementImpl(final AntElement parent, final XmlElement sourceElement) {
    this(parent, sourceElement, "name");
  }

  public AntStructuredElementImpl(final AntElement parent,
                                  final XmlElement sourceElement,
                                  final AntTypeDefinition definition,
                                  @NonNls final String nameElementAttribute) {
    this(parent, sourceElement, nameElementAttribute);
    myDefinition = definition;
    final AntTypeId id = new AntTypeId(getSourceElement().getName());
    if (definition != null && !definition.getTypeId().equals(id)) {
      myDefinition = new AntTypeDefinitionImpl((AntTypeDefinitionImpl)myDefinition);
      myDefinition.setTypeId(id);
      myDefinitionCloned = true;
    }
  }

  public AntStructuredElementImpl(final AntElement parent,
                                  final XmlElement sourceElement,
                                  final AntTypeDefinition definition) {
    this(parent, sourceElement, definition, "name");
  }

  @NotNull
  public XmlTag getSourceElement() {
    return (XmlTag)super.getSourceElement();
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
    if (hasNameElement()) {
      return getNameElement().getName();
    }
    if (hasIdElement()) {
      return getIdElement().getName();
    }
    return super.getName();
  }

  public PsiElement setName(String name) throws IncorrectOperationException {
    if (hasNameElement()) {
      getNameElement().setName(name);
      subtreeChanged();
    }
    else if (hasIdElement()) {
      getIdElement().setName(name);
      subtreeChanged();
    }
    else {
      super.setName(name);
    }
    return this;
  }

  public PsiElement findElementAt(int offset) {
    if (offset == myLastFoundElementOffset) {
      return myLastFoundElement;
    }
    final PsiElement foundElement = super.findElementAt(offset);
    if (foundElement != null) {
      myLastFoundElement = (AntElement)foundElement;
      myLastFoundElementOffset = offset;
    }
    return foundElement;
  }

  public AntTypeDefinition getTypeDefinition() {
    return myDefinition;
  }

  public void registerCustomType(final AntTypeDefinition def) {
    if (myDefinition != null) {
      if (!myDefinitionCloned) {
        myDefinition = new AntTypeDefinitionImpl((AntTypeDefinitionImpl)myDefinition);
        myDefinitionCloned = true;
      }
      myDefinition.registerNestedType(def.getTypeId(), def.getClassName());
    }
    getAntFile().registerCustomType(def);
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
        AntStructuredElementImpl se = (AntStructuredElementImpl)parent;
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

  public boolean hasNameElement() {
    return !isNameElement(ourNull);
  }

  public boolean hasIdElement() {
    return !isIdElement(ourNull);
  }

  public boolean isNameElement(PsiElement element) {
    return getNameElement() == element;
  }

  public boolean isIdElement(PsiElement element) {
    return getIdElement() == element;
  }

  public void clearCaches() {
    super.clearCaches();
    myReferencedElements = null;
    myIdElement = null;
    myNameElement = null;
    myLastFoundElementOffset = -1;
    myLastFoundElement = null;
  }


  public AntElement lightFindElementAt(int offset) {
    if (offset == myLastFoundElementOffset) {
      return myLastFoundElement;
    }
    return super.lightFindElementAt(offset);
  }

  public int getTextOffset() {
    if (hasNameElement()) {
      return getNameElement().getTextOffset();
    }
    if (hasIdElement()) {
      return getIdElement().getTextOffset();
    }
    return super.getTextOffset();
  }

  protected AntElement[] getChildrenInner() {
    final List<AntElement> children = new ArrayList<AntElement>();
    if (hasIdElement()) {
      children.add(getIdElement());
    }
    if (hasNameElement()) {
      children.add(getNameElement());
    }
    for (final PsiElement element : getSourceElement().getChildren()) {
      if (element instanceof XmlElement) {
        final AntElement antElement = AntElementFactory.createAntElement(this, (XmlElement)element);
        if (antElement != null) {
          children.add(antElement);
          if (antElement instanceof AntStructuredElement) {
            antElement.getChildren();
          }
        }
      }
    }
    return children.toArray(new AntElement[children.size()]);
  }

  @NotNull
  protected AntElement getIdElement() {
    if (myIdElement == null) {
      myIdElement = ourNull;
      AntElement parent = getAntParent();
      if (parent instanceof AntStructuredElement) {
        final XmlAttribute idAttr = getSourceElement().getAttribute("id", null);
        if (idAttr != null) {
          final XmlAttributeValue valueElement = idAttr.getValueElement();
          if (valueElement != null) {
            myIdElement = new AntNameElementImpl(this, valueElement);
            AntStructuredElement se = (AntStructuredElement)parent;
            se.registerRefId(myIdElement.getName(), this);
          }
        }
      }
    }
    return myIdElement;
  }

  @NotNull
  protected AntElement getNameElement() {
    if (myNameElement == null) {
      myNameElement = ourNull;
      XmlAttribute nameAttr = getSourceElement().getAttribute(myNameElementAttribute, null);
      if (nameAttr != null) {
        final XmlAttributeValue valueElement = nameAttr.getValueElement();
        if (valueElement != null) {
          myNameElement = new AntNameElementImpl(this, valueElement);
        }
      }
    }
    return myNameElement;
  }

  @NonNls
  protected String getNameElementAttribute() {
    return myNameElementAttribute;
  }
}
