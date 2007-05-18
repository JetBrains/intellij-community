/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package org.jetbrains.plugins.groovy.lang.completion.filters.modifiers;

import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.lang.completion.GroovyCompletionUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrConstructor;

/**
 * @author ilyas
 */
public class ThrowsFilter implements ElementFilter {

  public boolean isAcceptable(Object element, PsiElement context) {
    if (context.getParent() instanceof PsiErrorElement) {
      PsiElement candidate = GroovyCompletionUtil.nearestLeftSibling(context.getParent());
      if ((candidate instanceof GrMethod || candidate instanceof GrConstructor) &&
          candidate.getText().trim().endsWith(")")) {
        return true;
      }
    }
    if (context.getPrevSibling() instanceof PsiErrorElement) {
      PsiElement candidate = GroovyCompletionUtil.nearestLeftSibling(context.getPrevSibling());
      if ((candidate instanceof GrMethod || candidate instanceof GrConstructor) &&
          candidate.getText().trim().endsWith(")")) {
        return true;
      }
    }
    
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
