package org.jetbrains.plugins.groovy.dsl;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiWildcardType;

/**
 * @author peter
 */
public class GdslType {
  public final PsiType psiType;

  public GdslType(PsiType psiType) {
    this.psiType = psiType;
  }

  public String getName() {
    PsiType type = psiType;
    if (type instanceof PsiWildcardType) {
      type = ((PsiWildcardType)type).getBound();
    }
    if (type instanceof PsiClassType) {
      final PsiClass resolve = ((PsiClassType)type).resolve();
      if (resolve != null) {
        return resolve.getName();
      }
      final String canonicalText = type.getCanonicalText();
      final int i = canonicalText.indexOf('<');
      if (i < 0) return canonicalText;
      return canonicalText.substring(0, i);
    }

    if (type == null) {
      return "";
    }

    return type.getCanonicalText();
  }

}
