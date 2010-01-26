/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
public class GrDocCommentImpl extends LazyParseablePsiElement implements GroovyDocElementTypes, GrDocComment {
  public GrDocCommentImpl(CharSequence text) {
    super(GROOVY_DOC_COMMENT, text);
  }

  public String toString() {
    return "GrDocComment";
  }

  public IElementType getTokenType() {
    return getElementType();
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitElement(this);
  }

  public void acceptChildren(GroovyElementVisitor visitor) {
    PsiElement child = getFirstChild();
    while (child != null) {
      if (child instanceof GroovyPsiElement) {
        ((GroovyPsiElement)child).accept(visitor);
      }

      child = child.getNextSibling();
    }
  }

  public GrDocCommentOwner getOwner() {
    return GrDocCommentUtil.findDocOwner(this);
  }

  @NotNull
  public GrDocTag[] getTags() {
    final GrDocTag[] tags = PsiTreeUtil.getChildrenOfType(this, GrDocTag.class);
    return tags == null ? GrDocTag.EMPTY_ARRAY : tags;
  }

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

  @NotNull
  public GrDocTag[] findTagsByName(@NonNls String name) {
    if (!getText().contains(name)) return GrDocTag.EMPTY_ARRAY;
    ArrayList<GrDocTag> list = new ArrayList<GrDocTag>();
    for (PsiElement e = getFirstChild(); e != null; e = e.getNextSibling()) {
      if (e instanceof GrDocTag && ((GrDocTag)e).getName().equals(name)) {
        list.add((GrDocTag)e);
      }
    }
    return list.toArray(new GrDocTag[list.size()]);
  }

  public PsiElement[] getDescriptionElements() {
    ArrayList<PsiElement> array = new ArrayList<PsiElement>();
    for (PsiElement child = getFirstChild(); child != null; child = child.getNextSibling()) {
      final ASTNode node = child.getNode();
      if (node == null) continue;
      final IElementType i = node.getElementType();
      if (i == GDOC_TAG) break;
      if (i != mGDOC_COMMENT_START && i != mGDOC_COMMENT_END && i != mGDOC_ASTERISKS) {
        array.add(child);
      }
    }
    return array.toArray(new PsiElement[array.size()]);
  }
}
