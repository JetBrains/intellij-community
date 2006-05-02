package com.intellij.lang.ant.psi.impl.reference;

import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntProject;
import com.intellij.lang.ant.psi.AntStructuredElement;
import com.intellij.lang.ant.psi.AntTask;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.GenericReferenceProvider;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.util.IncorrectOperationException;

import java.util.HashSet;
import java.util.Set;

public class AntRefIdReference extends AntGenericReference {

  public AntRefIdReference(final GenericReferenceProvider provider,
                           final AntElement antElement,
                           final String str,
                           final TextRange textRange,
                           final XmlAttribute attribute) {
    super(provider, antElement, str, textRange, attribute);
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    final AntElement element = getElement();
    if (element instanceof AntStructuredElement) {
      getAttribute().setValue(newElementName);
      element.subtreeChanged();
    }
    return element;
  }

  public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
    if (element instanceof AntTask) {
      final PsiNamedElement psiNamedElement = (PsiNamedElement) element;
      return handleElementRename(psiNamedElement.getName());
    }
    throw new IncorrectOperationException("Can bind only to ant tasks.");
  }

  public String getUnresolvedMessagePattern() {
    return AntBundle.getMessage("cannot.resolve.refid", getCanonicalText());
  }

  public PsiElement resolve() {
    final AntStructuredElement element = (AntStructuredElement) getElement();
    return element.getElementByRefId(getCanonicalText());
  }

  public Object[] getVariants() {
    final AntProject project = getElement().getAntProject();
    return getVariants(project);
  }

  private static String[] getVariants(AntStructuredElement element) {
    final Set<String> variants = new HashSet<String>();
    appendSet(variants, element.getRefIds());
    for (PsiElement child : element.getChildren()) {
      if (child instanceof AntStructuredElement) {
        appendSet(variants, getVariants((AntStructuredElement) child));
      }
    }
    return variants.toArray(new String[variants.size()]);
  }

  private static void appendSet(final Set<String> set, final String[] strs) {
    for (String str : strs) {
      set.add(str);
    }
  }
}
