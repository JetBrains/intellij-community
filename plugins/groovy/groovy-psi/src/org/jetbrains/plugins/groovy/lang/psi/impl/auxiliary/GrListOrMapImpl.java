// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.findUsages.LiteralConstructorReference;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * @author ilyas
 */
public class GrListOrMapImpl extends GrExpressionImpl implements GrListOrMap {
  private static final TokenSet MAP_LITERAL_TOKEN_SET = TokenSet.create(GroovyElementTypes.NAMED_ARGUMENT, GroovyTokenTypes.mCOLON);

  private final PsiReference myLiteralReference = new LiteralConstructorReference(this);
  private volatile GrExpression[] myInitializers;
  private volatile GrNamedArgument[] myNamedArguments;

  public GrListOrMapImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitListOrMap(this);
  }

  public String toString() {
    return "Generalized list";
  }

  @Override
  public ASTNode addInternal(ASTNode first, ASTNode last, ASTNode anchor, Boolean before) {
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
  public PsiElement getLBrack() {
    return findChildByType(GroovyTokenTypes.mLBRACK);
  }

  @Override
  public PsiElement getRBrack() {
    return findChildByType(GroovyTokenTypes.mRBRACK);
  }

  @Override
  @NotNull
  public GrExpression[] getInitializers() {
    GrExpression[] initializers = myInitializers;
    if (initializers == null) {
      initializers = PsiTreeUtil.getChildrenOfType(this, GrExpression.class);
      initializers = initializers == null ? GrExpression.EMPTY_ARRAY : initializers;
      myInitializers = initializers;
    }
    return initializers;
  }

  @Override
  @NotNull
  public GrNamedArgument[] getNamedArguments() {
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
    return myLiteralReference;
  }

  @Override
  public void subtreeChanged() {
    myInitializers = null;
    myNamedArguments = null;
  }
}
