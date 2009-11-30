/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiImmediateClassType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrThisReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

/**
 * @author ilyas
 */
public class GrThisReferenceExpressionImpl extends GrExpressionImpl implements GrThisReferenceExpression {
  public GrThisReferenceExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitThisExpression(this);
  }

  public String toString() {
    return "'this' reference expression";
  }

  public PsiType getType() {
    final GrReferenceExpression qualifier = getQualifier();
    if (qualifier == null) {
      GroovyPsiElement context = PsiTreeUtil.getParentOfType(this, GrTypeDefinition.class, GroovyFile.class);
      if (context instanceof GrTypeDefinition) {
        return JavaPsiFacade.getInstance(getProject()).getElementFactory().createType((PsiClass)context);
      }
      else if (context instanceof GroovyFile) {
        final PsiClass scriptClass = ((GroovyFile)context).getScriptClass();
        if (scriptClass != null) return JavaPsiFacade.getInstance(getProject()).getElementFactory().createType(scriptClass);
      }
    }
    else {
      final PsiElement resolved = qualifier.resolve();
      if (resolved instanceof PsiClass) {
        return new PsiImmediateClassType((PsiClass)resolved, PsiSubstitutor.EMPTY);
      }
      else {
        try {
          return JavaPsiFacade.getElementFactory(getProject()).createTypeFromText(qualifier.getText(), this);
        }
        catch (IncorrectOperationException e) {
          return null;
        }
      }
    }

    return null;
  }

  @Nullable
  public GrReferenceExpression getQualifier() {
    return (GrReferenceExpression)findChildByType(GroovyElementTypes.REFERENCE_EXPRESSION);
  }
}
