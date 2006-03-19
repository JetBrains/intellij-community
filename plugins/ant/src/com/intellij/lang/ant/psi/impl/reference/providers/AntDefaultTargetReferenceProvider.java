package com.intellij.lang.ant.psi.impl.reference.providers;

import com.intellij.lang.ant.psi.AntProject;
import com.intellij.lang.ant.psi.impl.reference.AntTargetReference;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.xml.XmlAttribute;
import org.jetbrains.annotations.NotNull;

public class AntDefaultTargetReferenceProvider extends AntTargetReferenceProviderBase {

  @NotNull
  public PsiReference[] getReferencesByElement(PsiElement element) {
    final AntProject project = (AntProject)element;
    final XmlAttribute attr = project.getSourceElement().getAttribute("default", null);
    if (attr == null) {
      return PsiReference.EMPTY_ARRAY;
    }
    final int offsetInPosition = attr.getValueElement().getTextRange().getStartOffset() - project.getTextRange().getStartOffset() + 1;
    final String attrValue = attr.getValue();
    return new PsiReference[]{
      new AntTargetReference(this, project, attrValue, new TextRange(offsetInPosition, offsetInPosition + attrValue.length()), null)};
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
