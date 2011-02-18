package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrConstructorInvocation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrThisSuperReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;

/**
 * @author Maxim.Medvedev
 */
public abstract class GrThisSuperReferenceExpressionBase extends GrExpressionImpl implements GrThisSuperReferenceExpression {
  private static final OurResolver OUR_RESOLVER = new OurResolver();

  public GrThisSuperReferenceExpressionBase(ASTNode node) {
    super(node);
  }

  @Nullable
  public GrReferenceExpression getQualifier() {
    return (GrReferenceExpression)findChildByType(GroovyElementTypes.REFERENCE_EXPRESSION);
  }

  @Override
  public void setQualifier(@Nullable GrReferenceExpression newQualifier) {
    final GrExpression oldQualifier = getQualifier();
    final ASTNode node = getNode();
    final PsiElement refNameElement = getLastChild();
    assert refNameElement != null;
    if (newQualifier == null) {
      if (oldQualifier != null) {
        node.removeRange(node.getFirstChildNode(), refNameElement.getNode());
      }
    }
    else {
      if (oldQualifier != null) {
        node.replaceChild(oldQualifier.getNode(), newQualifier.getNode());
      }
      else {
        node.addChild(newQualifier.getNode(), refNameElement.getNode());
        node.addLeaf(GroovyTokenTypes.mDOT, ".", refNameElement.getNode());
      }
    }
  }

  @Override
  public PsiElement getElement() {
    return this;
  }

  @Override
  public TextRange getRangeInElement() {
    final PsiElement token = findNotNullChildByType(TokenSet.create(GroovyElementTypes.kTHIS, GroovyElementTypes.kSUPER));
    return TextRange.from(token.getStartOffsetInParent(), token.getTextLength());
  }

  @Override
  public PsiElement resolve() {
    final ResolveResult[] results = getManager().getResolveCache().resolveWithCaching(this, OUR_RESOLVER, false, false);
    if (results.length == 1) return results[0].getElement();
    return null;
  }

  @Nullable
  protected PsiElement resolveInner() {
    final PsiElement parent = getParent();
    if (parent instanceof GrConstructorInvocation) {
      return ((GrConstructorInvocation)parent).resolveMethod();
    }
    return null;
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    return this;
  }

  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    return this;
  }

  public boolean isReferenceTo(PsiElement element) {
    return getManager().areElementsEquivalent(element, resolve());
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

  @NotNull
  @Override
  public String getCanonicalText() {
    return getReferenceName();
  }

  static class OurResolver implements ResolveCache.PolyVariantResolver<GrThisSuperReferenceExpressionBase> {
    @Override
    public ResolveResult[] resolve(GrThisSuperReferenceExpressionBase ref, boolean incompleteCode) {
      final PsiElement resolved = ref.resolveInner();
      if (resolved == null) return ResolveResult.EMPTY_ARRAY;
      return new ResolveResult[]{new GroovyResolveResultImpl(resolved, true)};
    }
  }
}
