package com.intellij.lang.ant.psi.impl.reference.providers;

import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntProject;
import com.intellij.lang.ant.psi.AntTarget;
import com.intellij.lang.ant.psi.impl.AntProjectImpl;
import com.intellij.lang.ant.psi.impl.reference.AntTargetReference;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.GenericReferenceProvider;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.xml.XmlAttribute;
import org.jetbrains.annotations.NotNull;

public class AntDefaultTargetReferenceProvider extends GenericReferenceProvider {

  final AntProjectImpl myProject;
  final XmlAttribute myAttribute;

  public AntDefaultTargetReferenceProvider(final AntProjectImpl project, final XmlAttribute attribute) {
    myProject = project;
    myAttribute = attribute;
  }

  @NotNull
  public PsiReference[] getReferencesByElement(PsiElement element) {
    final int offsetInProject = myAttribute.getValueElement().getTextRange().getStartOffset() - myProject.getTextRange().getStartOffset() + 1;
    return getReferencesByString(myAttribute.getValue(), myProject, new ReferenceType(ReferenceType.ANT_TARGET), offsetInProject);
  }

  @NotNull
  public PsiReference[] getReferencesByElement(PsiElement element, ReferenceType type) {
    final int offsetInProject = myAttribute.getValueElement().getTextRange().getStartOffset() - myProject.getTextRange().getStartOffset();
    return getReferencesByString(myAttribute.getValue(), myProject, type, offsetInProject);
  }

  @NotNull
  public PsiReference[] getReferencesByString(String str, PsiElement position, ReferenceType type, int offsetInPosition) {
    return new PsiReference[]{
      new AntTargetReference(this, (AntElement)position, str, new TextRange(offsetInPosition, offsetInPosition + str.length()))};
  }

  public void handleEmptyContext(PsiScopeProcessor processor, PsiElement position) {
    AntProject project = (AntProject)position;
    final AntTarget[] targets = project.getTargets();
    for (final AntTarget target : targets) {
      if (!processor.execute(target, PsiSubstitutor.EMPTY)) return;
    }
  }

}
