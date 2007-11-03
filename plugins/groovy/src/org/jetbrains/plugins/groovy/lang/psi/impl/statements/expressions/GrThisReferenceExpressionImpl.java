/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
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
    GroovyPsiElement context = PsiTreeUtil.getParentOfType(this, GrTypeDefinition.class, GroovyFile.class);
    if (context instanceof GrTypeDefinition) {
      return getManager().getElementFactory().createType((PsiClass)context);
    } else if (context instanceof GroovyFile) {
      final PsiClass scriptClass = ((GroovyFile) context).getScriptClass();
      if (scriptClass != null) return getManager().getElementFactory().createType(scriptClass);
    }

    return null;
  }
}
