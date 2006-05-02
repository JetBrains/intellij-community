package com.intellij.lang.ant.psi.impl.reference;

import com.intellij.lang.ant.psi.AntElement;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.impl.source.resolve.reference.impl.GenericReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.GenericReferenceProvider;
import com.intellij.psi.xml.XmlAttribute;

public abstract class AntGenericReference extends GenericReference {

  private static final ReferenceType ourRefType = new ReferenceType(ReferenceType.UNKNOWN);

  private final AntElement myAntElement;
  private final String myText;
  private final TextRange myTextRange;
  private final XmlAttribute myAttribute;

  protected AntGenericReference(final GenericReferenceProvider provider,
                                final AntElement antElement,
                                final String str,
                                final TextRange textRange,
                                final XmlAttribute attribute) {
    super(provider);
    myAntElement = antElement;
    myText = str;
    myTextRange = textRange;
    myAttribute = attribute;
  }

  public AntElement getElement() {
    return myAntElement;
  }

  public PsiElement getContext() {
    return null;
  }

  public PsiReference getContextReference() {
    return null;
  }

  public boolean needToCheckAccessibility() {
    return false;
  }

  public TextRange getRangeInElement() {
    return myTextRange;
  }

  public String getCanonicalText() {
    return myText;
  }

  public ReferenceType getType() {
    return ourRefType;
  }

  public ReferenceType getSoftenType() {
    return ourRefType;
  }

  protected XmlAttribute getAttribute() {
    return myAttribute;
  }
}