package com.intellij.lang.ant.psi.impl.reference.providers;

import com.intellij.lang.ant.misc.PsiReferenceListSpinAllocator;
import com.intellij.lang.ant.psi.AntStructuredElement;
import com.intellij.lang.ant.psi.impl.reference.AntAttributeReference;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.GenericReferenceProvider;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlToken;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class AntAttributeReferenceProvider extends GenericReferenceProvider {

  @SuppressWarnings({"HardCodedStringLiteral"})
  @NotNull
  public PsiReference[] getReferencesByElement(PsiElement element) {
    if (!(element instanceof AntStructuredElement)) {
      return PsiReference.EMPTY_ARRAY;
    }
    final AntStructuredElement se = (AntStructuredElement)element;
    final int elementStartOffset = se.getTextRange().getStartOffset();
    final List<PsiReference> list = PsiReferenceListSpinAllocator.alloc();
    try {
      for (PsiElement child : se.getSourceElement().getChildren()) {
        if (child instanceof XmlToken) {
          XmlToken token = (XmlToken)child;
          // TODO: move XmlTokenType to openAPI
          if (token.getTokenType().toString().equals("XML_TAG_END")) {
            break;
          }
        }
        else if (child instanceof PsiWhiteSpace) {
          final int off = child.getTextRange().getStartOffset() - elementStartOffset;
          final String text = child.getText();
          list.add(new AntAttributeReference(this, se, text, new TextRange(off, off + text.length()), null));
        }
        else if (child instanceof XmlAttribute) {
          final PsiElement nameElement = child.getFirstChild();
          if (nameElement != null) {
            final int off = nameElement.getTextRange().getStartOffset() - elementStartOffset;
            final String text = nameElement.getText();
            list.add(new AntAttributeReference(this, se, text, new TextRange(off, off + text.length()), null));
          }
        }
      }
      final int count = list.size();
      return (count == 0) ? PsiReference.EMPTY_ARRAY : list.toArray(new PsiReference[count]);
    }
    finally {
      PsiReferenceListSpinAllocator.dispose(list);
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
