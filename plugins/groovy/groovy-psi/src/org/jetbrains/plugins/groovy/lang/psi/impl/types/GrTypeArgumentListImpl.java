// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.types;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiListLikeElement;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;

import java.util.Arrays;
import java.util.List;

import static com.intellij.psi.util.PsiTreeUtil.countChildrenOfType;

/**
 * @author Dmitry.Krasilschikov
 */
public class GrTypeArgumentListImpl extends GroovyPsiElementImpl implements GrTypeArgumentList, PsiListLikeElement {

  public GrTypeArgumentListImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitTypeArgumentList(this);
  }

  @Override
  public String toString() {
    return "Type arguments";
  }

  @Override
  public int getTypeArgumentCount() {
    return countChildrenOfType(this, GrTypeElement.class);
  }

  @Override
  public GrTypeElement[] getTypeArgumentElements() {
    return findChildrenByClass(GrTypeElement.class);
  }

  @Override
  public PsiType[] getTypeArguments() {
    final GrTypeElement[] elements = getTypeArgumentElements();
    if (elements.length == 0) return PsiType.EMPTY_ARRAY;

    PsiType[] result = PsiType.createArray(elements.length);
    for (int i = 0; i < elements.length; i++) {
      result[i] = elements[i].getType();
    }
    return result;
  }

  @Override
  public boolean isDiamond() {
    return findChildByClass(GrTypeElement.class) == null;
  }

  @Override
  public ASTNode addInternal(@NotNull ASTNode first, @NotNull ASTNode last, ASTNode anchor, Boolean before) {
    if (first == last && first.getPsi() instanceof GrTypeElement) {
      if (anchor == null) {
        anchor = getLastChild().getNode();
        before = true;
      }
      if (getTypeArgumentElements().length > 0) {
        if (before == null || before.booleanValue()) {
          getNode().addLeaf(GroovyTokenTypes.mCOMMA, ",", anchor);
        }
        else {
          getNode().addLeaf(GroovyTokenTypes.mCOMMA, ",", anchor.getTreeNext());
        }
      }

      return super.addInternal(first, last, anchor, before);
    }

    else {
      return super.addInternal(first, last, anchor, before);
    }
  }

  @Override
  public @NotNull List<? extends PsiElement> getComponents() {
    return Arrays.asList(getTypeArgumentElements());
  }
}
