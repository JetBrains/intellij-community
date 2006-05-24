package com.intellij.lang.ant.psi.impl.reference.providers;

import com.intellij.lang.ant.psi.AntStructuredElement;
import com.intellij.lang.ant.psi.AntTask;
import com.intellij.lang.ant.psi.impl.reference.AntElementNameReference;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.GenericReferenceProvider;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class AntElementNameReferenceProvider extends GenericReferenceProvider {

  @NotNull
  public PsiReference[] getReferencesByElement(PsiElement element) {
    if (!(element instanceof AntStructuredElement)) {
      return PsiReference.EMPTY_ARRAY;
    }
    final AntElementNameReference nameReference =
      new AntElementNameReference(this, (AntStructuredElement)element);
    if (element instanceof AntTask) {
      final AntTask task = (AntTask)element;
      if (!task.isMacroDefined()) {
        return PsiReference.EMPTY_ARRAY;
      }
      final XmlAttribute[] attrs = task.getSourceElement().getAttributes();
      if (attrs.length == 0) {
        return new PsiReference[]{nameReference};
      }
      List<PsiReference> result = new ArrayList<PsiReference>(attrs.length + 1);
      result.add(nameReference);
      for (XmlAttribute attr : attrs) {
        result.add(new AntElementNameReference(this, task, attr));
      }
      return result.toArray(new PsiReference[result.size()]);
    }
    final AntTask task = PsiTreeUtil.getParentOfType(element, AntTask.class);
    return (task == null || !task.isMacroDefined())
           ? PsiReference.EMPTY_ARRAY
           : new PsiReference[]{nameReference};
  }

  @NotNull
  public PsiReference[] getReferencesByElement(PsiElement element, ReferenceType type) {
    return getReferencesByElement(element);
  }

  @NotNull
  public PsiReference[] getReferencesByString(String str,
                                              PsiElement position,
                                              ReferenceType type,
                                              int offsetInPosition) {
    return getReferencesByElement(position);
  }
}
