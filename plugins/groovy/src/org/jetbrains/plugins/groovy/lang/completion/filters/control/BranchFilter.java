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

package org.jetbrains.plugins.groovy.lang.completion.filters.control;

import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.ElementFilter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrForStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrWhileStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseSection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

/**
 * @author ilyas
 */
public class BranchFilter implements ElementFilter {
  public boolean isAcceptable(Object element, PsiElement context) {
    if (context.getParent() != null) {
      PsiElement parent = context.getParent();
      return parent instanceof GrReferenceExpression && treeWalkUp(parent);
    }
    return false;
  }

  private boolean treeWalkUp(PsiElement elem) {
    PsiElement parent = elem.getParent();
    while (parent != null &&
        !(parent instanceof GrWhileStatement ||
            parent instanceof GrForStatement ||
            parent instanceof GrCaseSection)) {
      parent = parent.getParent();
    }
    return parent != null;
  }

  public boolean isClassAcceptable(Class hintClass) {
    return true;
  }

  @NonNls
  public String toString() {
    return "'break' and 'continue' keywords filter";
  }
}