package com.intellij.lang.ant.psi.impl.reference.providers;

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

import java.util.ArrayList;
import java.util.List;

public class AntMacroDefParameterReferenceProvider extends GenericReferenceProvider {

  @NotNull
  public PsiReference[] getReferencesByElement(PsiElement element) {
    if (!(element instanceof AntStructuredElement)) {
      return PsiReference.EMPTY_ARRAY;
    }
    AntStructuredElement antElement = (AntStructuredElement)element;
    AntAllTasksContainer sequential = PsiTreeUtil.getParentOfType(element, AntAllTasksContainer.class);
    if (sequential == null) {
      return PsiReference.EMPTY_ARRAY;
    }
    AntMacroDef macrodef = PsiTreeUtil.getParentOfType(sequential, AntMacroDef.class);
    if (macrodef == null) {
      return PsiReference.EMPTY_ARRAY;
    }
    final List<PsiReference> refs = new ArrayList<PsiReference>();
    for (XmlAttribute attr : antElement.getSourceElement().getAttributes()) {
      getXmlElementReferences(attr.getValueElement(), refs, antElement);
    }
    getXmlElementReferences(antElement.getSourceElement(), refs, antElement);
    return (refs.size() > 0) ? refs.toArray(new PsiReference[refs.size()]) : PsiReference.EMPTY_ARRAY;
  }

  private void getXmlElementReferences(final XmlElement element, final List<PsiReference> refs, final AntStructuredElement antElement) {
    final String text = element.getText();
    final int offsetInPosition = element.getTextRange().getStartOffset() - antElement.getTextRange().getStartOffset();
    int startIndex;
    int endIndex = -1;
    while ((startIndex = text.indexOf("@{", endIndex + 1)) > endIndex) {
      startIndex += 2;
      endIndex = text.indexOf('}', startIndex);
      if (endIndex < 0) break;
      if (endIndex > startIndex) {
        final String name = text.substring(startIndex, endIndex);
        refs.add(new AntMacroDefParameterReference(this, antElement, name,
                                                   new TextRange(offsetInPosition + startIndex, offsetInPosition + endIndex), element));
      }
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
