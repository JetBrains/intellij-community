package com.intellij.lang.ant.psi.impl.reference.providers;

import com.intellij.lang.ant.psi.AntTarget;
import com.intellij.lang.ant.psi.impl.reference.AntTargetReference;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import org.jetbrains.annotations.NotNull;

public class AntTargetListReferenceProvider extends AntTargetReferenceProviderBase {

  @NotNull
  public PsiReference[] getReferencesByElement(PsiElement element) {
    final AntTarget target = (AntTarget)element;
    final XmlAttribute attr = target.getSourceElement().getAttribute("depends", null);
    if (attr == null) {
      return PsiReference.EMPTY_ARRAY;
    }
    final XmlAttributeValue xmlAttributeValue = attr.getValueElement();
    if (xmlAttributeValue == null) {
      return PsiReference.EMPTY_ARRAY;
    }
    int offsetInPosition = xmlAttributeValue.getTextRange().getStartOffset() - target.getTextRange().getStartOffset() + 1;
    final String str = attr.getValue();
    final String[] targets = str.split(",");
    final int length = targets.length;
    if (length == 0) {
      return PsiReference.EMPTY_ARRAY;
    }
    PsiReference[] result = new PsiReference[length];
    for (int i = 0; i < result.length; i++) {
      final String t = targets[i].trim();
      result[i] = new AntTargetReference(this, target, t, new TextRange(offsetInPosition, offsetInPosition + t.length()), attr);
      offsetInPosition += t.length() + 1;
    }
    return result;
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
