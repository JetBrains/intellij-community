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
public class ThrowsFilter implements ElementFilter {
  public boolean isAcceptable(Object element, PsiElement context) {
/*
    if (context.getParent() != null &&
        context.getParent() instanceof GrExpression) {
      return true;
    }

*/
    return false;
  }

  public boolean isClassAcceptable(Class hintClass) {
    return true;
  }

  @NonNls
  public String toString() {
    return "'throws' keyword filter";
  }

}
