package com.intellij.lang.ant.psi.impl.reference;

import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntProperty;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.impl.source.resolve.reference.impl.GenericReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.GenericReferenceProvider;
import com.intellij.util.IncorrectOperationException;

public class AntPropertyFileReference extends GenericReference {

  private static final ReferenceType ourRefType = new ReferenceType(ReferenceType.FILE);

  private final AntElement myAntElement;
  private final String myText;
  private final TextRange myTextRange;

  public AntPropertyFileReference(final GenericReferenceProvider provider,
                                  final AntElement antElement,
                                  final String str,
                                  final TextRange textRange) {
    super(provider);
    myAntElement = antElement;
    myText = str;
    myTextRange = textRange;
  }

  public AntProperty getElement() {
    return (AntProperty)myAntElement;
  }

  public TextRange getRangeInElement() {
    return myTextRange;
  }

  public String getCanonicalText() {
    return myText;
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    final AntElement element = getElement();
    if (element instanceof AntProperty) {
      AntProperty property = (AntProperty)element;
      property.setPropertiesFile(newElementName);
    }
    return element;
  }

  public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
    if (element instanceof PropertiesFile) {
      final PsiNamedElement psiNamedElement = (PsiNamedElement)element;
      return handleElementRename(psiNamedElement.getName());
    }
    throw new IncorrectOperationException("Can bind only to properties files.");
  }

  public PsiElement getContext() {
    return null;
  }

  public PsiReference getContextReference() {
    return null;
  }

  public static ReferenceType getReferenceType() {
    return ourRefType;
  }

  public ReferenceType getType() {
    return getReferenceType();
  }

  public ReferenceType getSoftenType() {
    return getReferenceType();
  }

  public boolean needToCheckAccessibility() {
    return false;
  }

  public PsiElement resolve() {
    return getElement().getPropertiesFile();
  }
}
