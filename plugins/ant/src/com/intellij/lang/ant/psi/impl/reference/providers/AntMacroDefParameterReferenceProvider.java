package com.intellij.lang.ant.psi.impl.reference.providers;

import com.intellij.lang.ant.misc.PsiReferenceListSpinAllocator;
import com.intellij.lang.ant.psi.AntAllTasksContainer;
import com.intellij.lang.ant.psi.AntMacroDef;
import com.intellij.lang.ant.psi.AntStructuredElement;
import com.intellij.lang.ant.psi.impl.reference.AntMacroDefParameterReference;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.GenericReferenceProvider;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class AntMacroDefParameterReferenceProvider extends GenericReferenceProvider {

  @NotNull
  public PsiReference[] getReferencesByElement(PsiElement element) {
    if (!(element instanceof AntStructuredElement)) {
      return PsiReference.EMPTY_ARRAY;
    }
    final AntStructuredElement antElement = (AntStructuredElement)element;
    final AntAllTasksContainer sequential = PsiTreeUtil.getParentOfType(antElement, AntAllTasksContainer.class);
    if (sequential == null) {
      return PsiReference.EMPTY_ARRAY;
    }
    final AntMacroDef macrodef = PsiTreeUtil.getParentOfType(sequential, AntMacroDef.class);
    if (macrodef == null) {
      return PsiReference.EMPTY_ARRAY;
    }
    final List<PsiReference> refs = PsiReferenceListSpinAllocator.alloc();
    try {
      for (XmlAttribute attr : antElement.getSourceElement().getAttributes()) {
        getXmlElementReferences(attr.getValueElement(), refs, antElement);
      }
      getXmlElementReferences(antElement.getSourceElement(), refs, antElement);
      return (refs.size() > 0) ? refs.toArray(new PsiReference[refs.size()]) : PsiReference.EMPTY_ARRAY;
    }
    finally {
      PsiReferenceListSpinAllocator.dispose(refs);
    }
  }

  private void getXmlElementReferences(final XmlElement element, final List<PsiReference> refs, final AntStructuredElement antElement) {
    if (element == null) return;
    final String text = element.getText();
    final int offsetInPosition = element.getTextRange().getStartOffset() - antElement.getTextRange().getStartOffset();
    int startIndex;
    int endIndex = -1;
    while ((startIndex = text.indexOf("@{", endIndex + 1)) > endIndex) {
      startIndex += 2;
      endIndex = startIndex;
      int nestedBrackets = 0;
      while (text.length() > endIndex) {
        final char ch = text.charAt(endIndex);
        if (ch == '}') {
          if (nestedBrackets == 0) {
            break;
          }
          --nestedBrackets;
        }
        else if (ch == '{') {
          ++nestedBrackets;
        }
        ++endIndex;
      }
      if(nestedBrackets > 0 || endIndex == text.length()) return;
      if (endIndex >= startIndex) {
        final String name = text.substring(startIndex, endIndex);
        refs.add(new AntMacroDefParameterReference(this, antElement, name,
                                                   new TextRange(offsetInPosition + startIndex, offsetInPosition + endIndex), element));
      }
      endIndex = startIndex; 
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
