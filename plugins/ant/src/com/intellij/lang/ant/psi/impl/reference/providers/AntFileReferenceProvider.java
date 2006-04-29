package com.intellij.lang.ant.psi.impl.reference.providers;

import com.intellij.lang.ant.psi.AntStructuredElement;
import com.intellij.lang.ant.psi.impl.reference.AntFileReference;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.GenericReferenceProvider;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import org.jetbrains.annotations.NotNull;

public class AntFileReferenceProvider extends GenericReferenceProvider {

  @NotNull
  public PsiReference[] getReferencesByElement(PsiElement element) {
    AntStructuredElement antElement = (AntStructuredElement)element;
    final XmlAttribute attr = antElement.getSourceElement().getAttribute("file", null);
    if (attr == null) {
      return PsiReference.EMPTY_ARRAY;
    }
    final XmlAttributeValue xmlAttributeValue = attr.getValueElement();
    if( xmlAttributeValue == null) {
      return PsiReference.EMPTY_ARRAY;
    }
    final int offsetInPosition = xmlAttributeValue.getTextRange().getStartOffset() - antElement.getTextRange().getStartOffset() + 1;
    final String attrValue = attr.getValue();
    return new AntFileReference[]{
      new AntFileReference(this, antElement, attrValue, new TextRange(offsetInPosition, offsetInPosition + attrValue.length()))};
  }

  @NotNull
  public PsiReference[] getReferencesByElement(PsiElement element, ReferenceType type) {
    return getReferencesByElement(element);
  }

  @NotNull
  public PsiReference[] getReferencesByString(String str, PsiElement position, ReferenceType type, int offsetInPosition) {
    return getReferencesByElement(position);
  }
}
