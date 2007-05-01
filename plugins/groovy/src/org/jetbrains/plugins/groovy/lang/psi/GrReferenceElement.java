package org.jetbrains.plugins.groovy.lang.psi;

import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiElement;

/**
 * @author ven
 */
public interface GrReferenceElement extends GroovyPsiElement, PsiReference {
  String getReferenceName();
  PsiElement getReferenceNameElement();

  PsiElement resolve();
}
