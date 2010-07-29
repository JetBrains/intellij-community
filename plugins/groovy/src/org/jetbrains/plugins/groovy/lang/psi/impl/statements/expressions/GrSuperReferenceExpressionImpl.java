/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrSuperReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

/**
 * @author ilyas
 */
public class GrSuperReferenceExpressionImpl extends GrThisSuperReferenceExpressionBase implements GrSuperReferenceExpression {
  public GrSuperReferenceExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitSuperExpression(this);
  }

  public String toString() {
    return "'super' reference expression";
  }

  public PsiType getType() {
    final GrReferenceExpression qualifier = getQualifier();
    if (qualifier == null) {
      GroovyPsiElement context = PsiTreeUtil.getParentOfType(this, GrTypeDefinition.class, GroovyFile.class);
      if (context instanceof GrTypeDefinition) {
        final PsiClass superClass = ((GrTypeDefinition)context).getSuperClass();
        if (superClass != null) {
          return JavaPsiFacade.getInstance(getProject()).getElementFactory().createType(superClass);
        }
      }
      else if (context instanceof GroovyFileBase) {
        return JavaPsiFacade.getInstance(getProject()).getElementFactory()
          .createTypeByFQClassName(GrTypeDefinition.DEFAULT_BASE_CLASS_NAME, getResolveScope());
      }

    }
    else {
      final PsiElement resolved = qualifier.resolve();
      if (resolved instanceof PsiClass) {
        return getSuperType((PsiClass)resolved);
      }
    }
    return null;
  }

  @Nullable
  private PsiType getSuperType(PsiClass aClass) {
    if (aClass.isInterface()) {
      return PsiType.getJavaLangObject(getManager(), getResolveScope());
    }
    if (aClass instanceof GrAnonymousClassDefinition) {
      final PsiClassType baseClassType = ((GrAnonymousClassDefinition)aClass).getBaseClassType();
      final PsiClass psiClass = baseClassType.resolve();
      if (psiClass != null && !psiClass.isInterface()) {
        return baseClassType;
      }

      return PsiType.getJavaLangObject(getManager(), getResolveScope());
    }

    if ("java.lang.Object".equals(aClass.getQualifiedName())) return null;
    PsiClassType[] superTypes = aClass.getExtendsListTypes();
    if (superTypes.length == 0) {
      return PsiType.getJavaLangObject(getManager(), getResolveScope());
    }

    return superTypes[0];
  }

  @NotNull
  @Override
  public String getCanonicalText() {
    return "super";
  }
}
