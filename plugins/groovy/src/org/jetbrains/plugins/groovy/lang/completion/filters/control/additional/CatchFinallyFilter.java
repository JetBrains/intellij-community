/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.completion.filters.control.additional;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.filters.ElementFilter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.lang.completion.GroovyCompletionUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrTryCatchStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

/**
 * @author ilyas
 */
public class CatchFinallyFilter implements ElementFilter {
  public boolean isAcceptable(Object element, PsiElement context) {
    if (context != null &&
        GroovyCompletionUtil.nearestLeftSibling(context) instanceof GrTryCatchStatement) {
      GrTryCatchStatement tryStatement = (GrTryCatchStatement) GroovyCompletionUtil.nearestLeftSibling(context);
      if (tryStatement == null) return false;
      if (tryStatement.getFinallyClause() == null) {
        return true;
      }
    }
    if (context != null &&
        GroovyCompletionUtil.nearestLeftSibling(context) instanceof PsiErrorElement &&
        GroovyCompletionUtil.nearestLeftSibling(context).getPrevSibling() instanceof GrTryCatchStatement) {
      GrTryCatchStatement tryStatement = (GrTryCatchStatement) GroovyCompletionUtil.nearestLeftSibling(context).getPrevSibling();
      if (tryStatement == null) return false;
      if (tryStatement.getFinallyClause() == null) {
        return true;
      }
    }
    if (context != null &&
        (context.getParent() instanceof GrReferenceExpression || context.getParent() instanceof PsiErrorElement) &&
        GroovyCompletionUtil.nearestLeftSibling(context.getParent()) instanceof GrTryCatchStatement) {
      GrTryCatchStatement tryStatement = (GrTryCatchStatement) GroovyCompletionUtil.nearestLeftSibling(context.getParent());
      if (tryStatement == null) return false;
      if (tryStatement.getFinallyClause() == null) {
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
    return "filter fo 'catch' and 'finally' keywords";
  }

}
