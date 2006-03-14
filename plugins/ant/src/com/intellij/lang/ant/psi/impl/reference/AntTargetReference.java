package com.intellij.lang.ant.psi.impl.reference;

import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntTarget;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.impl.source.resolve.reference.impl.GenericReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.GenericReferenceProvider;
import com.intellij.util.IncorrectOperationException;

public class AntTargetReference extends GenericReference {
  private AntElement myAntElement;
  private String myText;
  private TextRange myTextRange;

  public AntTargetReference(GenericReferenceProvider provider, final AntElement antElement, final String str, final TextRange textRange) {
    super(provider);

    myAntElement = antElement;
    myText = str;
    myTextRange = textRange;
  }


  public PsiElement getElement() {
    return myAntElement;
  }

  public TextRange getRangeInElement() {
    return myTextRange;
  }

  public String getCanonicalText() {
    return myText;
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    return getManipulator(getElement()).handleContentChange(getElement(), getRangeInElement(), newElementName);
  }

  public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
    if(element instanceof AntTarget) {
      final PsiNamedElement psiNamedElement = (PsiNamedElement)element;
      return getManipulator(getElement()).handleContentChange(getElement(), getRangeInElement(), psiNamedElement.getName());
    }
    throw new IncorrectOperationException("Can bind only to ant targets.");
  }

  public PsiElement getContext() {
    return null;
  }

  public PsiReference getContextReference() {
    return null;
  }

  public ReferenceType getType() {
    return new ReferenceType(ReferenceType.ANT_TARGET);
  }

  public ReferenceType getSoftenType() {
    return getType();
  }

  public boolean needToCheckAccessibility() {
    return false;
  }
}
