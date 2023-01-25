// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.arguments;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiListLikeElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GrArgumentListImpl extends GroovyPsiElementImpl implements GrArgumentList, PsiListLikeElement {

  public GrArgumentListImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitArgumentList(this);
  }

  @Override
  public String toString() {
    return "Arguments";
  }

  @Override
  public GrNamedArgument @NotNull [] getNamedArguments() {
    List<GrNamedArgument> result = new ArrayList<>();
    for (PsiElement cur = this.getFirstChild(); cur != null; cur = cur.getNextSibling()) {
      if (cur instanceof GrNamedArgument) result.add((GrNamedArgument)cur);
    }
    return result.toArray(GrNamedArgument.EMPTY_ARRAY);
  }

  @Override
  public GrNamedArgument findNamedArgument(@NotNull String label) {
    return PsiImplUtil.findNamedArgument(this, label);
  }

  @Override
  public GrExpression @NotNull [] getExpressionArguments() {
    List<GrExpression> result = new ArrayList<>();
    for (PsiElement cur = this.getFirstChild(); cur != null; cur = cur.getNextSibling()) {
      if (cur instanceof GrExpression) result.add((GrExpression)cur);
    }
    return result.toArray(GrExpression.EMPTY_ARRAY);
  }

  @Override
  public GroovyPsiElement @NotNull [] getAllArguments() {
    List<GroovyPsiElement> args = new ArrayList<>();
    for (PsiElement child = getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof GrNamedArgument || child instanceof GrExpression) args.add((GroovyPsiElement)child);
    }
    return args.toArray(GroovyPsiElement.EMPTY_ARRAY);
  }

  @Override
  public GrArgumentList replaceWithArgumentList(GrArgumentList newArgList) throws IncorrectOperationException {
    if (this.getParent() == null || this.getParent().getNode() == null) {
      throw new IncorrectOperationException();
    }
    ASTNode parentNode = this.getParent().getNode();
    ASTNode newNode = newArgList.getNode();
    assert parentNode != null;
    parentNode.replaceChild(this.getNode(), newNode);
    if (!(newNode.getPsi() instanceof GrArgumentList)) {
      throw new IncorrectOperationException();
    }
    return ((GrArgumentList)newNode.getPsi());
  }

  @Override
  @Nullable
  public PsiElement getLeftParen() {
    ASTNode paren = getNode().findChildByType(GroovyTokenTypes.mLPAREN);
    return paren != null ? paren.getPsi() : null;
  }

  @Override
  @Nullable
  public PsiElement getRightParen() {
    ASTNode paren = getNode().findChildByType(GroovyTokenTypes.mRPAREN);
    return paren != null ? paren.getPsi() : null;
  }

  @Override
  public int getExpressionArgumentIndex(final GrExpression arg) {
    int res = 0;

    for (PsiElement cur = getFirstChild(); cur != null; cur = cur.getNextSibling()) {
      if (cur instanceof GrExpression) {
        if (arg == cur) return res;
        res++;
      }
    }

    return -1;
  }

  @Override
  public GrNamedArgument addNamedArgument(final GrNamedArgument namedArgument) {
    final GrNamedArgument[] namedArguments = getNamedArguments();
    final GrExpression[] args = getExpressionArguments();
    PsiElement anchor = null;
    final int namedCount = namedArguments.length;
    final int exprCount = args.length;
    if (namedCount > 0) {
      anchor = namedArguments[namedCount - 1];
    }
    else if (exprCount > 0) {
      anchor = args[exprCount - 1];
    }

    if (anchor != null) {
      anchor = PsiUtil.getNextNonSpace(anchor);
    }
    else {
      anchor = getRightParen();
    }

    addBefore(namedArgument, anchor);
    return namedArgument;
  }

  @Override
  public PsiElement add(@NotNull PsiElement element) throws IncorrectOperationException {
    return addBefore(element, null);
  }

  @Override
  public PsiElement addBefore(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    if (element instanceof GrNamedArgument || element instanceof GrExpression) {
      if (anchor == null) anchor = getLastChild();
      if (anchor == null) {
        return super.addBefore(element, anchor);
      }
      else {
        anchor = anchor.getPrevSibling();
      }
      while (anchor != null && !(anchor instanceof GrExpression) && !(anchor instanceof GrNamedArgument)) {
        anchor = anchor.getPrevSibling();
      }
      return addAfter(element, anchor);
    }
    return super.addBefore(element, anchor);
  }

  @Override
  public PsiElement addAfter(@NotNull PsiElement element, @Nullable PsiElement anchor) throws IncorrectOperationException {
    if (element instanceof GrExpression || element instanceof GrNamedArgument) {
      final boolean insertComma = getAllArguments().length != 0;

      if (anchor == null) anchor = getLeftParen();

      PsiElement result;
      result = super.addAfter(element, anchor);
      if (insertComma) {
        final ASTNode astNode = getNode();
        if (anchor == getLeftParen()) {
          astNode.addLeaf(GroovyTokenTypes.mCOMMA, ",", result.getNextSibling().getNode());
        }
        else {
          astNode.addLeaf(GroovyTokenTypes.mCOMMA, ",", result.getNode());
        }
        CodeStyleManager.getInstance(getManager().getProject()).reformat(this);
      }

      return result;
    }
    return super.addAfter(element, anchor);
  }

  @Override
  public void deleteChildInternal(@NotNull ASTNode child) {
    PsiElement element = child.getPsi();
    if (element instanceof GrExpression || element instanceof GrNamedArgument) {
      ASTNode prev = TreeUtil.skipElementsBack(child.getTreePrev(), TokenSets.WHITE_SPACES_OR_COMMENTS);
      if (prev != null && prev.getElementType() == GroovyTokenTypes.mCOMMA) {
        final ASTNode pprev = prev.getTreePrev();
        if (pprev != null && PsiImplUtil.isWhiteSpaceOrNls(pprev)) {
          super.deleteChildInternal(pprev);
        }
        super.deleteChildInternal(prev);
      }
      else {
        ASTNode next = TreeUtil.skipElements(child.getTreeNext(), TokenSets.WHITE_SPACES_OR_COMMENTS);
        if (next != null && next.getElementType() == GroovyTokenTypes.mCOMMA) {
          final ASTNode nnext = next.getTreeNext();
          if (nnext != null && PsiImplUtil.isWhiteSpaceOrNls(nnext)) {
            super.deleteChildInternal(nnext);
          }
          super.deleteChildInternal(next);
        }
      }
    }
    super.deleteChildInternal(child);
  }

  @Override
  public PsiExpression @NotNull [] getExpressions() {
    return PsiExpression.EMPTY_ARRAY;
  }

  @Override
  public PsiType @NotNull [] getExpressionTypes() {
    return PsiType.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  public List<? extends PsiElement> getComponents() {
    return Arrays.asList(getAllArguments());
  }
}
