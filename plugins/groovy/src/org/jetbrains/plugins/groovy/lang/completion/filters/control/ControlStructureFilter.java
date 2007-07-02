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

package org.jetbrains.plugins.groovy.lang.completion.filters.control;

import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.PsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrWhileStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrForStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseBlock;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.completion.GroovyCompletionUtil;
import org.jetbrains.annotations.NonNls;

/**
 * @author ilyas
 */


public class ControlStructureFilter implements ElementFilter {
  public boolean isAcceptable(Object element, PsiElement context) {
    final int offset = context.getTextRange().getStartOffset();
    if (GroovyCompletionUtil.isNewStatement(context, true)) {
      final PsiElement leaf = GroovyCompletionUtil.getLeafByOffset(offset - 1, context);
      if (leaf != null) {
        PsiElement parent = leaf.getParent();
        if (parent instanceof GroovyFile ||
            parent instanceof GrOpenBlock ||
            parent instanceof  GrClosableBlock) {
          return true;
        }
        if (parent instanceof GrCaseBlock &&
            ((GrCaseBlock) parent).getCaseLabels().length != 0 &&
            ((GrCaseBlock) parent).getCaseLabels()[0].getTextRange().getStartOffset() < offset){
          return true;
        }
      }
    }

    if (context.getParent() != null) {
      PsiElement parent = context.getParent();

      if (parent instanceof GrExpression &&
          parent.getParent() instanceof GroovyFile) {
        return true;
      }

      if (parent instanceof GrReferenceExpression) {

        PsiElement superParent = parent.getParent();

        if (superParent instanceof GrOpenBlock ||
            superParent instanceof GrClosableBlock ||
            superParent instanceof GrCaseBlock ||
            superParent instanceof GrIfStatement ||
            superParent instanceof GrForStatement ||
            superParent instanceof GrWhileStatement) {
          return true;
        }
      }

      return false;
    }

    return false;
  }

  public boolean isClassAcceptable(Class hintClass) {
    return true;
  }

  @NonNls
  public String toString() {
    return "Control structure keywords filter";
  }
}