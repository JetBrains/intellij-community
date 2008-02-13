package com.intellij.lang.ant.psi.impl.reference.providers;

import com.intellij.lang.ant.misc.PsiReferenceListSpinAllocator;
import com.intellij.lang.ant.psi.AntStructuredElement;
import com.intellij.lang.ant.psi.AntTask;
import com.intellij.lang.ant.psi.impl.reference.AntElementNameReference;
import com.intellij.lang.ant.psi.impl.reference.AntEndElementNameReference;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.ProcessingContext;
import com.intellij.xml.util.XmlTagUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class AntElementNameReferenceProvider extends PsiReferenceProvider {

  @NotNull
  public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull final ProcessingContext context) {
    if (!(element instanceof AntStructuredElement)) {
      return PsiReference.EMPTY_ARRAY;
    }
    final AntStructuredElement se = (AntStructuredElement)element;
    final List<PsiReference> result = PsiReferenceListSpinAllocator.alloc();
    try {
      result.add(new AntElementNameReference(se));
      if (element instanceof AntTask) {
        final AntTask task = (AntTask)element;
        if (task.isMacroDefined()) {
          final XmlAttribute[] attrs = task.getSourceElement().getAttributes();
          if (attrs.length != 0) {
            for (XmlAttribute attr : attrs) {
              result.add(new AntElementNameReference(task, attr));
            }
          }
        }
      }
      final AntElementNameReference endReference = findEndElementNameReference(se);
      if (endReference != null) {
        result.add(endReference);
      }
      return result.toArray(new PsiReference[result.size()]);
    }
    finally {
      PsiReferenceListSpinAllocator.dispose(result);
    }
  }

  @Nullable
  private static AntEndElementNameReference findEndElementNameReference(final AntStructuredElement element) {
    final XmlTag tag = element.getSourceElement();
    final PsiElement endTagElement = XmlTagUtil.getEndTagNameElement(tag);
    if (endTagElement == null) {
      return null;
    }
    final TextRange elementRange = element.getTextRange();
    final TextRange endTagRange = endTagElement.getTextRange();
    final TextRange refRange = new TextRange(
      endTagRange.getStartOffset() - elementRange.getStartOffset(),
      endTagRange.getEndOffset() - elementRange.getStartOffset()
    );
    return new AntEndElementNameReference(element, refRange, isClosed(endTagElement));
  }

  private static boolean isClosed(PsiElement current) {
    for (PsiElement e = current; e != null; e = e.getNextSibling()) {
      if (e instanceof XmlToken) {
        final IElementType tokenType = ((XmlToken)e).getTokenType();
        if (tokenType == XmlTokenType.XML_TAG_END || tokenType == XmlTokenType.XML_EMPTY_ELEMENT_END) {
          return true;
        }
      }
    }
    return false;
  }
}
