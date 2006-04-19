package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntStructuredElement;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.lang.ant.psi.introspection.AntTypeId;
import com.intellij.lang.ant.psi.introspection.impl.AntTypeDefinitionImpl;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class AntStructuredElementImpl extends AntElementImpl implements AntStructuredElement {
  protected AntTypeDefinition myDefinition;
  protected boolean myDefinitionCloned = false;

  public AntStructuredElementImpl(final AntElement parent, final XmlElement sourceElement) {
    super(parent, sourceElement);
  }

  public AntStructuredElementImpl(final AntElement parent, final XmlElement sourceElement, final AntTypeDefinition definition) {
    this(parent, sourceElement);
    myDefinition = definition;
    final AntTypeId id = new AntTypeId(getSourceElement().getName(), getSourceElement().getNamespace());
    if (definition != null && !definition.getTypeId().equals(id)) {
      myDefinition = new AntTypeDefinitionImpl((AntTypeDefinitionImpl)myDefinition);
      myDefinition.setTypeId(id);
      myDefinitionCloned = true;
    }
  }

  @NotNull
  public XmlTag getSourceElement() {
    return (XmlTag)super.getSourceElement();
  }

  public String toString() {
    @NonNls StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append("AntStructuredElement");
      final XmlTag sourceElement = getSourceElement();
      builder.append("[");
      builder.append(sourceElement.getName());
      builder.append("]");
      return builder.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  public String getName() {
    return getSourceElement().getName();
  }

  public PsiElement setName(String name) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  protected AntElement[] getChildrenInner() {
    final XmlTag[] tags = getSourceElement().getSubTags();
    final List<AntElement> children = new ArrayList<AntElement>();
    for (final XmlTag tag : tags) {
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
        myDefinition = new AntTypeDefinitionImpl((AntTypeDefinitionImpl)myDefinition);
        myDefinitionCloned = true;
      }
      myDefinition.registerNestedType(def.getTypeId(), def.getClassName());
      getAntProject().registerCustomType(def);
    }
  }
}
