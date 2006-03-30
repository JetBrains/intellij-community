package com.intellij.lang.ant.psi.impl.reference.providers;

import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.impl.reference.AntPropertyFileReference;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.GenericReferenceProvider;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

public class AntPropertyFileReferenceProvider extends GenericReferenceProvider {

  @NotNull
  public PsiReference[] getReferencesByElement(PsiElement element) {
    AntElement antElement = (AntElement)element;
    final XmlAttribute attr = ((XmlTag)antElement.getSourceElement()).getAttribute("file", null);
    if (attr == null) {
      return PsiReference.EMPTY_ARRAY;
    }
    final int offsetInPosition = attr.getValueElement().getTextRange().getStartOffset() - antElement.getTextRange().getStartOffset() + 1;
    final String attrValue = attr.getValue();
    return new AntPropertyFileReference[]{
      new AntPropertyFileReference(this, antElement, attrValue, new TextRange(offsetInPosition, offsetInPosition + attrValue.length()))};
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
