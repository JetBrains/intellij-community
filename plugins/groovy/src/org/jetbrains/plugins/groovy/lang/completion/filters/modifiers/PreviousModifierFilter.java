/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package org.jetbrains.plugins.groovy.lang.completion.filters.modifiers;

import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NonNls;

/**
 * @author ilyas
 */
public class PreviousModifierFilter implements ElementFilter {
  public boolean isAcceptable(Object element, PsiElement context) {
    String[] modifiers = new String[]{"private", "public", "protected", "static", "transient", "final", "abstract",
        "native", "threadsafe", "volatile", "strictfp", "synchronized"};
    if (element instanceof PsiElement) {
      PsiElement psiElement = (PsiElement) element;
      for (String modifier : modifiers) {
        if (modifier.equals(psiElement.getText().trim())) {
          return true;
        }
      }
    }
    return false;
  }

  public boolean isClassAcceptable(Class hintClass) {
    return true;
  }

  @NonNls
  public String toString() {
    return "Second filter for modifier keywords";
  }

}