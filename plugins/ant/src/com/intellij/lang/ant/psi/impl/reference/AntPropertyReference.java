package com.intellij.lang.ant.psi.impl.reference;

import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntProperty;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.GenericReferenceProvider;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.util.IncorrectOperationException;

public class AntPropertyReference extends AntGenericReference {

  private static final ReferenceType ourRefType = new ReferenceType(ReferenceType.ANT_PROPERTY);

  public AntPropertyReference(final GenericReferenceProvider provider,
                              final AntElement antElement,
                              final String str,
                              final TextRange textRange,
                              final XmlAttribute attribute) {
    super(provider, antElement, str, textRange, attribute);
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    final XmlAttribute attribute = getAttribute();
    final String oldName = getCanonicalText();
    final String value = attribute.getValue();
    attribute.setValue(value.replace("${" + oldName + '}', "${" + newElementName + '}'));
    final AntElement element = getElement();
    element.subtreeChanged();
    return element;
  }

  public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
    if (element instanceof AntProperty) {
      final PsiNamedElement psiNamedElement = (PsiNamedElement)element;
      return handleElementRename(psiNamedElement.getName());
    }
    throw new IncorrectOperationException("Can bind only to ant properties.");
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

  public String getUnresolvedMessagePattern() {
    return AntBundle.getMessage("unknown.property", getCanonicalText());
  }
}
