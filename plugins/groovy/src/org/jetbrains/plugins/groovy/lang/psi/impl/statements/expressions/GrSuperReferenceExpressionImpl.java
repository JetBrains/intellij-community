/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrSuperReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

/**
 * @author ilyas
 */
public class GrSuperReferenceExpressionImpl extends GrExpressionImpl implements GrSuperReferenceExpression {
  public GrSuperReferenceExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitSuperExpression(this);
  }

  public String toString() {
    return "'super'reference expression";
  }

  public PsiType getType() {
    GroovyPsiElement context = PsiTreeUtil.getParentOfType(this, GrTypeDefinition.class, GrClosableBlock.class, GroovyFile.class);
    if (context instanceof GrTypeDefinition) {
      final PsiClass superClass = ((GrTypeDefinition) context).getSuperClass();
      if (superClass != null) {
        return getManager().getElementFactory().createType(superClass);
      }
    } else if (context instanceof GroovyFile) {
      return getManager().getElementFactory().createTypeByFQClassName(GrTypeDefinition.DEFAULT_BASE_CLASS_NAME, getResolveScope());
    }

    return null;
  }
}
