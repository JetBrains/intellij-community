package com.intellij.lang.ant.psi.impl.reference;

import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntStructuredElement;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.GenericReferenceProvider;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.util.IncorrectOperationException;

import java.util.HashSet;
import java.util.Set;

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
    final AntElement element = getElement();
    final String oldName = getCanonicalText();
    if (!oldName.equals(newElementName)) {
      final XmlAttribute attribute = getAttribute();
      final String value = attribute.getValue();
      attribute.setValue(value.replace("${" + oldName + '}', "${" + newElementName + '}'));
      element.subtreeChanged();
    }
    return element;
  }

  public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
    final AntElement antElement = getElement();
    final AntElement parent = antElement.getAntParent();
    if (parent != null) {
      parent.setProperty(getCanonicalText(), element);
      return handleElementRename(((PsiNamedElement)element).getName());
    }
    return antElement;
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
    final String name = getCanonicalText();
    AntElement element = getElement();
    while (element != null) {
      final PsiElement psiElement = element.getProperty(name);
      if (psiElement != null) {
        return psiElement;
      }
      element = element.getAntParent();
    }
    return null;
  }

  public String getUnresolvedMessagePattern() {
    return AntBundle.getMessage("unknown.property", getCanonicalText());
  }

  public Object[] getVariants() {
    return getVariants(getElement().getAntProject());
  }

  private static PsiElement[] getVariants(AntStructuredElement element) {
    final Set<PsiElement> variants = new HashSet<PsiElement>();
    appendSet(variants, element.getProperties());
    for (PsiElement child : element.getChildren()) {
      if (child instanceof AntStructuredElement) {
        appendSet(variants, getVariants((AntStructuredElement)child));
      }
    }
    return variants.toArray(new PsiElement[variants.size()]);
  }

  private static void appendSet(final Set<PsiElement> set, final PsiElement[] elements) {
    for (final PsiElement element : elements) {
      set.add(element);
    }
  }
}
