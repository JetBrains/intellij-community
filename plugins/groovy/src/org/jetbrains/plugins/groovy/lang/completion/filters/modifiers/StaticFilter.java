/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package org.jetbrains.plugins.groovy.lang.completion.filters.modifiers;

import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.completion.GroovyCompletionUtil;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;

/**
 * @author ilyas
 */
public class StaticFilter extends ModifiersFilter{

  public boolean isAcceptable(Object element, PsiElement context) {
    if (context != null &&
        context.getParent() != null &&
        context.getParent().getParent() instanceof GrImportStatement) {
      PsiElement left = GroovyCompletionUtil.nearestLeftSibling(context.getParent());
      boolean b = left != null && left.getNode() != null && GroovyTokenTypes.kIMPORT.equals(left.getNode().getElementType());
      if (b) return b;
    }
    return super.isAcceptable(element, context);
  }

  @NonNls
  public String toString() {
    return "Filter for 'static' keyword";
  }

}
