package com.intellij.lang.ant.psi.impl.reference.providers;

import com.intellij.lang.ant.misc.PsiReferenceListSpinAllocator;
import com.intellij.lang.ant.psi.AntTarget;
import com.intellij.lang.ant.psi.impl.reference.AntTargetReference;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import org.jetbrains.annotations.NotNull;

import java.util.List;

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
    final List<PsiReference> result = PsiReferenceListSpinAllocator.alloc();
    try {
      for (final String t : targets) {
        int i = 0;
        final int len = t.length();
        for (; i < len; ++i) {
          if (!Character.isWhitespace(t.charAt(i))) break;
        }
        final String targetName = (i < len) ? t.substring(i).trim() : "";
        result.add(new AntTargetReference(this, target, targetName,
                                          new TextRange(offsetInPosition + i, offsetInPosition + i + targetName.length()), attr));
        offsetInPosition += len + 1;
      }
      return (result.size() > 0) ? result.toArray(new PsiReference[result.size()]) : PsiReference.EMPTY_ARRAY;
    }
    finally {
      PsiReferenceListSpinAllocator.dispose(result);
    }
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
