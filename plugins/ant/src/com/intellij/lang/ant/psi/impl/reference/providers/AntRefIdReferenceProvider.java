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
import org.jetbrains.annotations.NotNull;

public class AntRefIdReferenceProvider extends GenericReferenceProvider {

  @NotNull
  public PsiReference[] getReferencesByElement(PsiElement element) {
    if (element instanceof AntStructuredElement) {
      AntStructuredElement se = (AntStructuredElement)element;
      final XmlAttribute attr = se.getSourceElement().getAttribute("refid", null);
      if (attr != null) {
        final int offsetInPosition = attr.getValueElement().getTextRange().getStartOffset() - se.getTextRange().getStartOffset() + 1;
        final String attrValue = attr.getValue();
        return new PsiReference[]{
          new AntRefIdReference(this, se, attrValue, new TextRange(offsetInPosition, offsetInPosition + attrValue.length()), attr)};
      }
    }
    return PsiReference.EMPTY_ARRAY;
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
    AntElement element = (AntElement)position;
    while (true) {
      element = element.getAntParent();
      if (element == null) {
        return;
      }
      if (element instanceof AntStructuredElement) {
        AntStructuredElement se = (AntStructuredElement)element;
        final String[] refids = se.getRefIds();
        if (refids.length > 0) {
          for (String refid : refids) {
            if (!processor.execute(se.getElementByRefId(refid), PsiSubstitutor.EMPTY)) return;
          }
        }
      }
    }
  }
}
