/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
    return isInfixOperatorPosition(context);
  }

  public static boolean isInfixOperatorPosition(PsiElement context) {
    if (context.getParent() != null &&
        context.getParent() instanceof GrReferenceExpression &&
        context.getParent().getParent() != null &&
        context.getParent().getParent() instanceof GrCommandArgumentList) {
      return true;
    }
    if (GroovyCompletionUtil.nearestLeftSibling(context) instanceof PsiErrorElement &&
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