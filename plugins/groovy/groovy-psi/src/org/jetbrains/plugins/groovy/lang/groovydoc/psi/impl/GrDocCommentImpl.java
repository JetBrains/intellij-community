/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.groovy.lang.groovydoc.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
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

/**
 * @author ilyas
 */
public class GrDocCommentImpl extends LazyParseablePsiElement implements GrDocComment {
  public GrDocCommentImpl(CharSequence text) {
    super(GroovyDocElementTypes.GROOVY_DOC_COMMENT, text);
  }

  public String toString() {
    return "GrDocComment";
  }

  @Override
  public IElementType getTokenType() {
    return getElementType();
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitDocComment(this);
  }

  @Override
  public void acceptChildren(GroovyElementVisitor visitor) {
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
  @NotNull
  public GrDocTag[] getTags() {
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
  @NotNull
  public GrDocTag[] findTagsByName(@NonNls String name) {
    if (!getText().contains(name)) return GrDocTag.EMPTY_ARRAY;
    ArrayList<GrDocTag> list = new ArrayList<>();
    for (PsiElement e = getFirstChild(); e != null; e = e.getNextSibling()) {
      if (e instanceof GrDocTag && name.equals(((GrDocTag)e).getName())) {
        list.add((GrDocTag)e);
      }
    }
    return list.toArray(new GrDocTag[list.size()]);
  }

  @Override
  @NotNull
  public PsiElement[] getDescriptionElements() {
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
}
