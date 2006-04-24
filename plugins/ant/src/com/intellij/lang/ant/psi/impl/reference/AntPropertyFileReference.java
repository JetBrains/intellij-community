package com.intellij.lang.ant.psi.impl.reference;

import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntProperty;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.GenericReferenceProvider;
import com.intellij.util.IncorrectOperationException;

public class AntPropertyFileReference extends AntGenericReference {

  private static final ReferenceType ourRefType = new ReferenceType(ReferenceType.FILE);

  public AntPropertyFileReference(final GenericReferenceProvider provider,
                                  final AntElement antElement,
                                  final String str,
                                  final TextRange textRange) {
    super(provider, antElement, str, textRange, null);
  }

  public AntProperty getElement() {
    return (AntProperty)super.getElement();
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

  public static ReferenceType getReferenceType() {
    return ourRefType;
  }

  public ReferenceType getType() {
    return getReferenceType();
  }

  public ReferenceType getSoftenType() {
    return getReferenceType();
  }

  public PsiElement resolve() {
    return getElement().getPropertiesFile();
  }

  public String getUnresolvedMessagePattern() {
    return AntBundle.getMessage("properties.file.doesnt.exist", getCanonicalText());
  }
}