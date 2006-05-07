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
  private int myLastFoundElementOffset = -1;
  private PsiElement myLastFoundElement;

  public AntStructuredElementImpl(final AntElement parent, final XmlElement sourceElement) {
    super(parent, sourceElement);
    getIdElement();
    getNameElement();
  }

  public AntStructuredElementImpl(final AntElement parent, final XmlElement sourceElement, final AntTypeDefinition definition) {
    this(parent, sourceElement);
    myDefinition = definition;
    final AntTypeId id = new AntTypeId(getSourceElement().getName(), null);
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
    if (getNameElement() != ourNull) {
      return getNameElement().getName();
    }
    if (getIdElement() != ourNull) {
      return getIdElement().getName();
    }
    return super.getName();
  }

  public PsiElement setName(String name) throws IncorrectOperationException {
    if (getNameElement() != ourNull) {
      getNameElement().setName(name);
      subtreeChanged();
    } else if (getIdElement() != ourNull) {
      getIdElement().setName(name);
      subtreeChanged();
    } else {
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
      myLastFoundElement = foundElement;
      myLastFoundElementOffset = offset;
    }
    return foundElement;
  }

  public AntTypeDefinition getTypeDefinition() {
    return myDefinition;
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
    myIdElement = null;
    myNameElement = null;
    myLastFoundElementOffset = -1;
    myLastFoundElement = null;
  }

  public int getTextOffset() {
    if (getNameElement() != ourNull) {
      return getNameElement().getTextOffset();
    }
    if (getIdElement() != ourNull) {
      return getIdElement().getTextOffset();
    }
    return super.getTextOffset();
  }

  protected AntElement[] getChildrenInner() {
    final List<AntElement> children = new ArrayList<AntElement>();
    AntElement idElement = getIdElement();
    if (idElement != ourNull) {
      children.add(idElement);
    }
    AntElement nameElement = getNameElement();
    if (nameElement != ourNull) {
      children.add(nameElement);
    }
    for (final PsiElement element : getSourceElement().getChildren()) {
      if (element instanceof XmlElement) {
        final AntElement antElement =
            AntElementFactory.createAntElement(this, (XmlElement) element);
        if (antElement != null) {
          children.add(antElement);
        }
      }
    }
    return children.toArray(new AntElement[children.size()]);
  }

  @NotNull
  private AntElement getIdElement() {
    if (myIdElement == null) {
      myIdElement = ourNull;
      AntElement parent = getAntParent();
      if (parent instanceof AntStructuredElement) {
        final XmlAttribute idAttr = getSourceElement().getAttribute("id", null);
        if (idAttr != null) {
          AntStructuredElement se = (AntStructuredElement) parent;
          myIdElement = new AntNameElementImpl(this, idAttr.getValueElement());
          se.registerRefId(myIdElement.getName(), this);
        }
      }
    }
    return myIdElement;
  }

  @NotNull
  private AntElement getNameElement() {
    if (myNameElement == null) {
      myNameElement = ourNull;
      XmlAttribute nameAttr = getSourceElement().getAttribute("name", null);
      if (nameAttr != null) {
        myNameElement = new AntNameElementImpl(this, nameAttr.getValueElement());
      }
    }
    return myNameElement;
  }
}
