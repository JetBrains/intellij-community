// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.groovydoc.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.impl.source.tree.LazyParseablePsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.groovydoc.lexer.GroovyDocTokenTypes;
import org.jetbrains.plugins.groovy.lang.groovydoc.parser.GroovyDocElementTypes;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocCommentOwner;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocTag;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;

import java.util.ArrayList;

public class GrDocCommentImpl extends LazyParseablePsiElement implements GrDocComment {
  public GrDocCommentImpl(CharSequence text) {
    super(GroovyDocElementTypes.GROOVY_DOC_COMMENT, text);
  }

  @Override
  public String toString() {
    return "GrDocComment";
  }

  @NotNull
  @Override
  public IElementType getTokenType() {
    return getElementType();
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitDocComment(this);
  }

  @Override
  public void acceptChildren(@NotNull GroovyElementVisitor visitor) {
    PsiElement child = getFirstChild();
    while (child != null) {
      if (child instanceof GroovyPsiElement) {
        ((GroovyPsiElement)child).accept(visitor);
      }

      child = child.getNextSibling();
    }
  }

  @Override
  public GrDocCommentOwner getOwner() {
    return GrDocCommentUtil.findDocOwner(this);
  }

  @Override
  public GrDocTag @NotNull [] getTags() {
    final GrDocTag[] tags = PsiTreeUtil.getChildrenOfType(this, GrDocTag.class);
    return tags == null ? GrDocTag.EMPTY_ARRAY : tags;
  }

  @Override
  @Nullable
  public GrDocTag findTagByName(@NonNls String name) {
    if (!getText().contains(name)) return null;
    for (PsiElement e = getFirstChild(); e != null; e = e.getNextSibling()) {
      if (e instanceof GrDocTag && ((GrDocTag)e).getName().equals(name)) {
        return (GrDocTag)e;
      }
    }
    return null;
  }

  @Override
  public GrDocTag @NotNull [] findTagsByName(@NonNls String name) {
    if (!getText().contains(name)) return GrDocTag.EMPTY_ARRAY;
    ArrayList<GrDocTag> list = new ArrayList<>();
    for (PsiElement e = getFirstChild(); e != null; e = e.getNextSibling()) {
      if (e instanceof GrDocTag && name.equals(((GrDocTag)e).getName())) {
        list.add((GrDocTag)e);
      }
    }
    return list.toArray(GrDocTag.EMPTY_ARRAY);
  }

  @Override
  public PsiElement @NotNull [] getDescriptionElements() {
    ArrayList<PsiElement> array = new ArrayList<>();
    for (PsiElement child = getFirstChild(); child != null; child = child.getNextSibling()) {
      final ASTNode node = child.getNode();
      if (node == null) continue;
      final IElementType i = node.getElementType();
      if (i == GroovyDocElementTypes.GDOC_TAG) break;
      if (i != GroovyDocTokenTypes.mGDOC_COMMENT_START && i != GroovyDocTokenTypes.mGDOC_COMMENT_END && i != GroovyDocTokenTypes.mGDOC_ASTERISKS) {
        array.add(child);
      }
    }
    return PsiUtilCore.toPsiElementArray(array);
  }

  @Override
  public PsiReference @NotNull [] getReferences() {
    return ReferenceProvidersRegistry.getReferencesFromProviders(this);
  }
}
