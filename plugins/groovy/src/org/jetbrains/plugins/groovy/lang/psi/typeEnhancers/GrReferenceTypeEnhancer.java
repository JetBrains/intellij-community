package org.jetbrains.plugins.groovy.lang.psi.typeEnhancers;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

/**
 * @author Sergey Evdokimov
 */
public abstract class GrReferenceTypeEnhancer {

  public static final ExtensionPointName<GrReferenceTypeEnhancer> EP_NAME = ExtensionPointName.create("org.intellij.groovy.referenceTypeEnhancer");

  @Nullable
  public abstract PsiType getReferenceType(GrReferenceExpression ref, @Nullable PsiElement resolved);

}
