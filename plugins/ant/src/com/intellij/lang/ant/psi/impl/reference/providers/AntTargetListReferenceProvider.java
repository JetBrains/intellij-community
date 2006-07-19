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
import com.intellij.util.StringBuilderSpinAllocator;
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
    final String value = attr.getValue();
    final List<PsiReference> result = PsiReferenceListSpinAllocator.alloc();
    try {
      final StringBuilder builder = StringBuilderSpinAllocator.alloc();
      try {
        int i = 0;
        int rightBound;
        final int valueLen = value.length();
        do {
          rightBound = (i < valueLen) ? value.indexOf(',', i) : valueLen;
          if (rightBound < 0) rightBound = valueLen;
          builder.setLength(0);
          int j = i;
          for (; j < rightBound; ++j) {
            builder.append(value.charAt(j));
          }
          j = 0;
          final int len = builder.length();
          for (; j < len; ++j) {
            if (!Character.isWhitespace(builder.charAt(j))) break;
          }
          final String targetName = (len == 0 || j == len) ? "" : builder.substring(j);
          result.add(new AntTargetReference(this, target, targetName,
                                            new TextRange(offsetInPosition + i + j, offsetInPosition + i + j + targetName.length()), attr));
          i = rightBound + 1;
        }
        while (rightBound < valueLen);
        return (result.size() > 0) ? result.toArray(new PsiReference[result.size()]) : PsiReference.EMPTY_ARRAY;
      }
      finally {
        StringBuilderSpinAllocator.dispose(builder);
      }
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
