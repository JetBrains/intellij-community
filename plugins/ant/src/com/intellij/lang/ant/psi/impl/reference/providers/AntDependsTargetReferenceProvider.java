package com.intellij.lang.ant.psi.impl.reference.providers;

import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntTarget;
import com.intellij.lang.ant.psi.impl.reference.AntTargetReference;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

public class AntDependsTargetReferenceProvider extends AntTargetReferenceProviderBase {

  @NotNull
  public PsiReference[] getReferencesByElement(PsiElement element) {
    return getReferencesByElement(element, AntTargetReference.getReferenceType());
  }

  @NotNull
  public PsiReference[] getReferencesByElement(PsiElement element, ReferenceType type) {
    final AntTarget target = (AntTarget)element;
    final XmlAttribute attr = ((XmlTag)target.getSourceElement()).getAttribute("depends", null);
    if (attr == null) {
      return PsiReference.EMPTY_ARRAY;
    }
    final int offsetInProject = attr.getValueElement().getTextRange().getStartOffset() - target.getTextRange().getStartOffset() + 1;
    return getReferencesByString(attr.getValue(), target, type, offsetInProject);
  }

  @NotNull
  public PsiReference[] getReferencesByString(String str, PsiElement position, ReferenceType type, int offsetInPosition) {
    final String[] targets = str.split(",");
    final int length = targets.length;
    if (length == 0) {
      return PsiReference.EMPTY_ARRAY;
    }
    PsiReference[] result = new PsiReference[length];
    for (int i = 0; i < result.length; i++) {
      final String target = targets[i];
      result[i] = new AntTargetReference(this, (AntElement)position, target,
                                         new TextRange(offsetInPosition, offsetInPosition + target.length()));
      offsetInPosition += target.length() + 1;
    }
    return result;
  }
}
