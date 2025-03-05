// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiListLikeElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyConstructorReference;
import org.jetbrains.plugins.groovy.lang.resolve.references.GrLiteralConstructorReference;

import java.util.List;

public class GrListOrMapImpl extends GrExpressionImpl implements GrListOrMap, PsiListLikeElement {

  private static final TokenSet MAP_LITERAL_TOKEN_SET = TokenSet.create(GroovyElementTypes.NAMED_ARGUMENT, GroovyTokenTypes.mCOLON);

  private final GroovyConstructorReference myConstructorReference = new GrLiteralConstructorReference(this);
  private volatile GrExpression[] myInitializers;
  private volatile GrNamedArgument[] myNamedArguments;

  public GrListOrMapImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitListOrMap(this);
  }

  @Override
  public String toString() {
    return "Generalized list";
  }

  @Override
  public ASTNode addInternal(@NotNull ASTNode first, @NotNull ASTNode last, ASTNode anchor, Boolean before) {
    if (getInitializers().length == 0) {
      return super.addInternal(first, last, getNode().getFirstChildNode(), false);
    }
    final ASTNode lastChild = getNode().getLastChildNode();
    getNode().addLeaf(GroovyTokenTypes.mCOMMA, ",", lastChild);
    return super.addInternal(first, last, lastChild.getTreePrev(), false);
  }

  @Override
  public void deleteChildInternal(@NotNull ASTNode child) {
    final PsiElement psi = child.getPsi();
    if (psi instanceof GrExpression || psi instanceof GrNamedArgument) {
      PsiElement prev = PsiUtil.getPrevNonSpace(psi);
      PsiElement next = PsiUtil.getNextNonSpace(psi);
      if (prev != null && prev.getNode() != null && prev.getNode().getElementType() == GroovyTokenTypes.mCOMMA) {
        super.deleteChildInternal(prev.getNode());
      }
      else if (next instanceof LeafPsiElement && next.getNode() != null && next.getNode().getElementType() == GroovyTokenTypes.mCOMMA) {
        super.deleteChildInternal(next.getNode());
      }
    }
    super.deleteChildInternal(child);
  }

  @Override
  public boolean isMap() {
    return findChildByType(MAP_LITERAL_TOKEN_SET) != null;
  }

  @Override
  public boolean isEmpty() {
    return getInitializers().length == 0 && getNamedArguments().length == 0;
  }

  @Override
  public @NotNull PsiElement getLBrack() {
    return findNotNullChildByType(GroovyTokenTypes.mLBRACK);
  }

  @Override
  public @Nullable PsiElement getRBrack() {
    return findChildByType(GroovyTokenTypes.mRBRACK);
  }

  @Override
  public GrExpression @NotNull [] getInitializers() {
    GrExpression[] initializers = myInitializers;
    if (initializers == null) {
      initializers = PsiTreeUtil.getChildrenOfType(this, GrExpression.class);
      initializers = initializers == null ? GrExpression.EMPTY_ARRAY : initializers;
      myInitializers = initializers;
    }
    return initializers;
  }

  @Override
  public GrNamedArgument @NotNull [] getNamedArguments() {
    GrNamedArgument[] namedArguments = myNamedArguments;
    if (namedArguments == null) {
      namedArguments = PsiTreeUtil.getChildrenOfType(this, GrNamedArgument.class);
      namedArguments = namedArguments == null ? GrNamedArgument.EMPTY_ARRAY : namedArguments;
      myNamedArguments = namedArguments;
    }
    return namedArguments;
  }

  @Override
  public GrNamedArgument findNamedArgument(@NotNull String label) {
    return PsiImplUtil.findNamedArgument(this, label);
  }

  @Override
  public PsiReference getReference() {
    return getConstructorReference();
  }

  @Override
  public @Nullable GroovyConstructorReference getConstructorReference() {
    return myConstructorReference.resolveClass() != null ? myConstructorReference : null;
  }

  @Override
  public void subtreeChanged() {
    myInitializers = null;
    myNamedArguments = null;
  }

  @Override
  public @NotNull List<? extends PsiElement> getComponents() {
    return PsiTreeUtil.getChildrenOfAnyType(this, GrExpression.class, GrNamedArgument.class);
  }
}
