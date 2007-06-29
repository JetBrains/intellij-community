/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpr;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;

/**
 *
 */
public class PsiImplUtil {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil");
  public static GrExpression replaceExpression(GrExpression oldExpr, GrExpression newExpr) throws IncorrectOperationException {
    if (oldExpr.getParent() == null ||
            oldExpr.getParent().getNode() == null) {
      throw new IncorrectOperationException();
    }
    // Remove unnecessary parentheses
    if (oldExpr.getParent() instanceof GrParenthesizedExpr &&
        newExpr instanceof GrReferenceExpression){
      return ((GrExpression) oldExpr.getParent()).replaceWithExpression(newExpr);
    }
    ASTNode parentNode = oldExpr.getParent().getNode();
    ASTNode newNode = newExpr.getNode();
    parentNode.replaceChild(oldExpr.getNode(), newNode);
    if (!(newNode.getPsi() instanceof GrExpression)){
      throw new IncorrectOperationException();
    }
    return ((GrExpression) newNode.getPsi());
  }

  public static void shortenReferences(GroovyPsiElement element) {
    doShorten(element);
  }

  private static void doShorten(PsiElement element) {
    PsiElement child = element.getFirstChild();
    while (child != null) {
      if (child instanceof GrReferenceElement) {
        final GrCodeReferenceElement ref = (GrCodeReferenceElement) child;
        if (ref.getQualifier() != null) {
          final PsiElement resolved = ref.resolve();
          if (resolved instanceof PsiClass) {
            ref.setQualifier(null);
            try {
              ref.bindToElement(resolved);
            } catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        }
      }

      doShorten(child);
      child = child.getNextSibling();
    }
  }

}
