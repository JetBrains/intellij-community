package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ResolveResult;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrConstructorInvocation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrThisSuperReferenceExpression;

/**
 * @author Maxim.Medvedev
 */
public abstract class GrThisSuperReferenceExpressionBase extends GrExpressionImpl implements GrThisSuperReferenceExpression {
  public GrThisSuperReferenceExpressionBase(ASTNode node) {
    super(node);
  }

  @Nullable
  public GrReferenceExpression getQualifier() {
    return (GrReferenceExpression)findChildByType(GroovyElementTypes.REFERENCE_EXPRESSION);
  }

  @Override
  public PsiElement getElement() {
    return this;
  }

  @Override
  public TextRange getRangeInElement() {
    return new TextRange(0, getTextLength());
  }

  @Override
  public PsiElement resolve() {
    final PsiElement parent = getParent();
    if (parent instanceof GrConstructorInvocation)return ((GrConstructorInvocation)parent).resolveConstructor();
    return null;
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    return this;
  }

  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    return this;
  }

  public boolean isReferenceTo(PsiElement element) {
    return element instanceof PsiMethod && ((PsiMethod)element).isConstructor() && getManager().areElementsEquivalent(element, resolve());
  }

  @NotNull
  public Object[] getVariants() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  public boolean isSoft() {
    return false;
  }

  @Override
  public PsiReference getReference() {
    return this;
  }

  @NotNull
  public ResolveResult[] multiResolve(boolean incompleteCode) {
    final PsiElement parent = getParent();
    if (parent instanceof GrConstructorInvocation) {
      return ((GrConstructorInvocation)parent).multiResolveConstructor();
    }
    return ResolveResult.EMPTY_ARRAY;
  }
}
