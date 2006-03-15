package com.intellij.lang.ant.psi.impl.reference.providers;

import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntProject;
import com.intellij.lang.ant.psi.impl.reference.AntTargetReference;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

public class AntDefaultTargetReferenceProvider extends AntTargetReferenceProviderBase {

  @NotNull
  public PsiReference[] getReferencesByElement(PsiElement element) {
    return getReferencesByElement(element, AntTargetReference.getReferenceType());
  }

  @NotNull
  public PsiReference[] getReferencesByElement(PsiElement element, ReferenceType type) {
    final AntProject project = (AntProject)element;
    final XmlAttribute attr = ((XmlTag)project.getSourceElement()).getAttribute("default", null);
    if (attr == null) {
      return PsiReference.EMPTY_ARRAY;
    }
    final int offsetInProject = attr.getValueElement().getTextRange().getStartOffset() - project.getTextRange().getStartOffset() + 1;
    return getReferencesByString(attr.getValue(), project, type, offsetInProject);
  }

  @NotNull
  public PsiReference[] getReferencesByString(String str, PsiElement position, ReferenceType type, int offsetInPosition) {
    return new PsiReference[]{
      new AntTargetReference(this, (AntElement)position, str, new TextRange(offsetInPosition, offsetInPosition + str.length()))};
  }
}
