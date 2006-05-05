package com.intellij.lang.ant.psi.impl.reference.providers;

import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntStructuredElement;
import com.intellij.lang.ant.psi.impl.reference.AntRefIdReference;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.GenericReferenceProvider;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import org.jetbrains.annotations.NotNull;

public class AntRefIdReferenceProvider extends GenericReferenceProvider {

  @NotNull
  public PsiReference[] getReferencesByElement(PsiElement element) {
    if (!(element instanceof AntStructuredElement)) {
      return PsiReference.EMPTY_ARRAY;
    }
    AntStructuredElement se = (AntStructuredElement) element;
    final XmlAttribute attr = se.getSourceElement().getAttribute("refid", null);
    if (attr == null) {
      return PsiReference.EMPTY_ARRAY;
    }
    final XmlAttributeValue valueElement = attr.getValueElement();
    if (valueElement == null) {
      return PsiReference.EMPTY_ARRAY;
    }
    final int offsetInPosition = valueElement.getTextRange().getStartOffset() - se.getTextRange().getStartOffset() + 1;
    final String attrValue = attr.getValue();
    return new PsiReference[]{
        new AntRefIdReference(this, se, attrValue, new TextRange(offsetInPosition, offsetInPosition + attrValue.length()), attr)};
  }

  @NotNull
  public PsiReference[] getReferencesByElement(PsiElement element, ReferenceType type) {
    return getReferencesByElement(element);
  }

  @NotNull
  public PsiReference[] getReferencesByString(String str, PsiElement position, ReferenceType type, int offsetInPosition) {
    return getReferencesByElement(position);
  }

  public void handleEmptyContext(PsiScopeProcessor processor, PsiElement position) {
    if (!(position instanceof AntStructuredElement)) return;
    AntStructuredElement element = (AntStructuredElement) position;
    while (element != null) {
      for( String refid:  element.getRefIds() ) {
        final AntElement ref = element.getElementByRefId(refid);
        if ( ref != null && !processor.execute(ref, PsiSubstitutor.EMPTY)) return;
      }
      element = (AntStructuredElement) element.getAntParent();
    }
  }
}