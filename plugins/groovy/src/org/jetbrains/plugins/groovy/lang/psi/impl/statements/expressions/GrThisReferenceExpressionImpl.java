/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrThisReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.util.PsiTreeUtil;

/**
 * @author ilyas
 */
public class GrThisReferenceExpressionImpl extends GroovyPsiElementImpl implements GrThisReferenceExpression {
  public GrThisReferenceExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "'this' reference expression";
  }

  public PsiType getType() {
    GroovyPsiElement context = PsiTreeUtil.getParentOfType(this, GrTypeDefinition.class, GrClosableBlock.class, GroovyFile.class);
    if (context instanceof GrTypeDefinition) {
      return getManager().getElementFactory().createType((PsiClass)context);
    } else if (context instanceof GroovyFile) {
      PsiClass scriptClass = getManager().findClass(GroovyFile.SCRIPT_BASE_CLASS_NAME);
      if (scriptClass != null) {
        return getManager().getElementFactory().createType(scriptClass);
      }
    }

    return null;
  }
}
