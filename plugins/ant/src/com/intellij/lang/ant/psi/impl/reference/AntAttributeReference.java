package com.intellij.lang.ant.psi.impl.reference;

import com.intellij.lang.ant.psi.AntStructuredElement;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.GenericReferenceProvider;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.util.IncorrectOperationException;

/**
 * Ant attribute reference serves only for completion.
 */
public class AntAttributeReference extends AntGenericReference {

  public AntAttributeReference(final GenericReferenceProvider provider,
                               final AntStructuredElement element,
                               final String str,
                               final TextRange textRange,
                               final XmlAttribute attribute) {
    super(provider, element, str, textRange, attribute);
  }

  public AntStructuredElement getElement() {
    return (AntStructuredElement)super.getElement();
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    return null;
  }

  public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
    return null;
  }

  public PsiElement resolve() {
    return null;
  }

  public Object[] getVariants() {
    final AntTypeDefinition def = getElement().getTypeDefinition();
    return (def == null) ? ourEmptyIntentions : def.getAttributes();
  }


  public boolean isCompletionOnlyReference() {
    return true;
  }
}
