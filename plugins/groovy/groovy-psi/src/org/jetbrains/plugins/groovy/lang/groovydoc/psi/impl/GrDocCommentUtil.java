// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.groovy.lang.groovydoc.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.groovydoc.parser.GroovyDocElementTypes;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocCommentOwner;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GroovyDocPsiElement;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMembersDeclaration;

/**
 * @author Maxim.Medvedev
 */
public abstract class GrDocCommentUtil {
  @Nullable
  public static GrDocCommentOwner findDocOwner(GroovyDocPsiElement docElement) {
    PsiElement element = docElement;
    while (element != null && element.getParent() instanceof GroovyDocPsiElement) element = element.getParent();
    if (element == null) return null;

    element = skipWhiteSpacesAndStopOnDoc(element, true);

    if (element instanceof GrDocCommentOwner) return (GrDocCommentOwner)element;
    if (element instanceof GrMembersDeclaration) {
      GrMember[] members = ((GrMembersDeclaration)element).getMembers();
      if (members.length > 0 && members[0] instanceof GrDocCommentOwner) {
        return (GrDocCommentOwner)members[0];
      }
    }
    return null;
  }

  @Nullable
  public static GrDocComment findDocComment(GrDocCommentOwner owner) {
    if (owner.getFirstChild() instanceof GrDocComment) {
      return ((GrDocComment)owner.getFirstChild());
    }

    PsiElement element = owner instanceof GrVariable && owner.getParent() instanceof GrVariableDeclaration ? owner.getParent() : owner;

    element = skipWhiteSpacesAndStopOnDoc(element, false);
    if (element instanceof GrDocComment) return (GrDocComment)element;
    return null;
  }

  private static PsiElement skipWhiteSpacesAndStopOnDoc(PsiElement element, boolean forward) {
    while (true) {
      element = forward ? element.getNextSibling() : element.getPrevSibling();
      if (element == null) break;
      final ASTNode node = element.getNode();
      if (node == null) break;
      if (GroovyDocElementTypes.GROOVY_DOC_COMMENT.equals(node.getElementType()) ||
          !TokenSets.WHITE_SPACES_OR_COMMENTS.contains(node.getElementType())) {
        break;
      }
    }
    return element;
  }

  public static GrDocComment setDocComment(@NotNull GrDocCommentOwner owner, @Nullable GrDocComment comment) {
    GrDocComment docComment = owner.getDocComment();

    if (docComment != null) {
      if (comment == null) {
        docComment.delete();
        return null;
      }
      else {
        PsiElement added = docComment.replace(comment);
        assert added instanceof GrDocComment;
        return (GrDocComment)added;
      }
    }
    else {
      if (comment == null) return null;

      PsiElement parent = owner.getParent();

      ASTNode node = owner.getNode();
      parent.getNode().addLeaf(GroovyTokenTypes.mNLS, "\n ", node);

      PsiElement added = parent.addBefore(comment, owner);
      assert added instanceof GrDocComment;

      return (GrDocComment)added;
    }
  }

}
