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
import org.jetbrains.plugins.groovy.lang.completion.GroovyCompletionData;
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
        GroovyCompletionData.nearestLeftSibling(context) instanceof PsiErrorElement &&
        GroovyCompletionData.nearestLeftSibling(context).getPrevSibling() instanceof  GrExpression) {
      return true;
    }
    if (context.getParent() instanceof PsiErrorElement){
      PsiElement leftSibling = GroovyCompletionData.nearestLeftSibling(context.getParent());
      if (leftSibling != null && leftSibling.getLastChild() instanceof GrExpression) {
        return true;
      }
    }
    if (context.getParent() instanceof GrReferenceExpression &&
        GroovyCompletionData.nearestLeftSibling(context.getParent()) instanceof PsiErrorElement &&
        GroovyCompletionData.nearestLeftSibling(context.getParent()).getPrevSibling() instanceof  GrExpression) {
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