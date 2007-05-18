/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package org.jetbrains.plugins.groovy.lang.completion.filters.exprs;

import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCommandArgumentList;
import org.jetbrains.plugins.groovy.lang.completion.GroovyCompletionUtil;
import org.jetbrains.annotations.NonNls;

/**
 * @author ilyas
 */
public class InstanceOfFilter implements ElementFilter {
  public boolean isAcceptable(Object element, PsiElement context) {
    if (context.getParent() != null &&
        context.getParent() instanceof GrReferenceExpression &&
        context.getParent().getParent() != null &&
        context.getParent().getParent() instanceof GrCommandArgumentList) {
      return true;
    }
    if (context != null &&
        GroovyCompletionUtil.nearestLeftSibling(context) instanceof PsiErrorElement &&
        GroovyCompletionUtil.endsWithExpression(GroovyCompletionUtil.nearestLeftSibling(context).getPrevSibling())) {
      return true;
    }
    if (context.getParent() instanceof PsiErrorElement) {
      PsiElement leftSibling = GroovyCompletionUtil.nearestLeftSibling(context.getParent());
      if (leftSibling != null && leftSibling.getLastChild() instanceof GrExpression) {
        return true;
      }
    }
    if (context.getParent() instanceof GrReferenceExpression &&
        GroovyCompletionUtil.nearestLeftSibling(context.getParent()) instanceof PsiErrorElement &&
        GroovyCompletionUtil.endsWithExpression(GroovyCompletionUtil.nearestLeftSibling(context.getParent()).getPrevSibling())) {
      return true;
    }
    if (context.getParent() instanceof PsiErrorElement &&
        GroovyCompletionUtil.endsWithExpression(GroovyCompletionUtil.nearestLeftSibling(context.getParent()))) {
      return true;
    }

    return false;
  }

  public boolean isClassAcceptable(Class hintClass) {
    return true;
  }

  @NonNls
  public String toString() {
    return "'instanceof' keyword filter";
  }
}