/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package org.jetbrains.plugins.groovy.lang.completion.filters.control;

import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.PsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrWhileStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrForStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseBlock;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.annotations.NonNls;

/**
 * @author ilyas
 */
public class BranchFilter implements ElementFilter {
  public boolean isAcceptable(Object element, PsiElement context) {
    if (context.getParent() != null) {
      PsiElement parent = context.getParent();

      if (parent instanceof GrReferenceExpression &&
          treeWalkUp(parent)) {
        PsiElement superParent = parent.getParent();
        if ((superParent instanceof GrOpenBlock ||
            superParent instanceof GrClosableBlock)) {
          return true;
        }

        if (superParent instanceof GrWhileStatement) {
          PsiElement elem = parent.getPrevSibling();
          while (elem != null &&
              !GroovyElementTypes.mRPAREN.equals(elem.getNode().getElementType())) {
            elem = elem.getPrevSibling();
          }
          if (elem != null) {
            return true;
          } else {
            return false;
          }
        }
      }

      return false;
    }
    return false;
  }

  private boolean treeWalkUp(PsiElement elem) {
    PsiElement parent = elem.getParent();
    while (parent != null &&
        !(parent instanceof GrWhileStatement ||
            parent instanceof GrForStatement ||
            parent instanceof GrCaseBlock)) {
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