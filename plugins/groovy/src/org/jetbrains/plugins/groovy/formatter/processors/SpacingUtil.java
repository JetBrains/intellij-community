/*
 * Copyright 2000-2008 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.formatter.processors;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.formatter.FormatterUtil;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.groovydoc.lexer.GroovyDocTokenTypes;
import org.jetbrains.plugins.groovy.lang.groovydoc.parser.GroovyDocElementTypes;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocMemberReference;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;

/**
 * @author ilyas
 */
public abstract class SpacingUtil {

  static boolean isWhiteSpace(final ASTNode treePrev) {
    return treePrev != null && (treePrev.getPsi() instanceof PsiWhiteSpace || treePrev.getTextLength() == 0);
  }

  static boolean canStickChildrenTogether(final ASTNode child1, final ASTNode child2) {
    return child1 == null || child2 == null || isWhiteSpace(child1) || isWhiteSpace(child2);
  }

  static boolean shouldKeepSpace(@NotNull final PsiElement parent) {
    ASTNode node = parent.getNode();
    if (node == null) return true;
    final IElementType type = node.getElementType();
    if (type == GroovyDocTokenTypes.mGDOC_TAG_VALUE_TOKEN) {
      return PsiTreeUtil.getParentOfType(parent, GrDocMemberReference.class) != null;
    }

    return type == GroovyTokenTypes.GROOVY_DOC_COMMENT || type == GroovyDocElementTypes.GDOC_TAG
        || type == GroovyDocElementTypes.GDOC_INLINED_TAG;
  }

  static ASTNode getLeafNonSpaceBefore(final ASTNode element) {
    if (element == null) return null;
    ASTNode treePrev = element.getTreePrev();
    if (treePrev != null) {
      ASTNode candidate = getLastChildOf(treePrev);
      if (candidate != null && !isWhiteSpace(candidate) && candidate.getTextLength() > 0) {
        return candidate;
      } else {
        return getLeafNonSpaceBefore(candidate);
      }
    }
    final ASTNode treeParent = element.getTreeParent();

    if (treeParent == null || treeParent.getTreeParent() == null) {
      return null;
    } else {
      return getLeafNonSpaceBefore(treeParent);
    }
  }

  private static ASTNode getLastChildOf(ASTNode element) {
    if (element == null) {
      return null;
    }
    if (element instanceof LeafElement) {
      return element;
    } else {
      final ASTNode node = element.getLastChildNode();
      if (node instanceof LeafElement) ChameleonTransforming.transform((LeafElement) node);
      final ASTNode lastChild = element.getLastChildNode();
      if (lastChild == null) {
        return element;
      } else {
        return getLastChildOf(lastChild);
      }
    }
  }

  static ASTNode getPrevElementType(final ASTNode child) {
    return FormatterUtil.getLeafNonSpaceBefore(child);
  }

  static TokenSet COMMENT_BIT_SET = GroovyTokenTypes.COMMENT_SET;
}
