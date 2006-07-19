package com.intellij.lang.ant.psi.impl.reference;

import com.intellij.lang.ant.psi.AntElement;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

public class AntEntityReference implements PsiReference {

  private final AntElement myElement;
  private final PsiReference myXmlRef;

  public AntEntityReference(final AntElement element, final PsiReference xmlRef) {
    myElement = element;
    myXmlRef = xmlRef;
  }

  public PsiElement getElement() {
    return myElement;
  }

  public TextRange getRangeInElement() {
    return myXmlRef.getRangeInElement();
  }

  @Nullable
  public PsiElement resolve() {
    return myXmlRef.resolve();
  }

  public String getCanonicalText() {
    return myXmlRef.getCanonicalText();
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    return myXmlRef.handleElementRename(newElementName);
  }

  public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
    return myXmlRef.bindToElement(element);
  }

  public boolean isReferenceTo(PsiElement element) {
    return myXmlRef.isReferenceTo(element);
  }

  public Object[] getVariants() {
    return EMPTY_ARRAY;
  }

  public boolean isSoft() {
    return myXmlRef.isSoft();
  }
}
