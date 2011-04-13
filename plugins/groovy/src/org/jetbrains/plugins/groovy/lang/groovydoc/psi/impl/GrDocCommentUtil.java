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
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocCommentOwner;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GroovyDocPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;

import static org.jetbrains.plugins.groovy.lang.groovydoc.parser.GroovyDocElementTypes.*;
import static org.jetbrains.plugins.groovy.lang.lexer.TokenSets.*;

/**
 * @author Maxim.Medvedev
 */
public abstract class GrDocCommentUtil {
  @Nullable
  public static GrDocCommentOwner findDocOwner(GroovyDocPsiElement docElement) {
    PsiElement element = docElement;
    while (element != null && element.getParent() instanceof GroovyDocPsiElement) element = element.getParent();
    if (element == null) return null;

    while (true) {
      element = element.getNextSibling();
      if (element == null) return null;
      final ASTNode node = element.getNode();
      if (node == null) return null;
      if (GROOVY_DOC_COMMENT.equals(node.getElementType()) ||
          !WHITE_SPACES_OR_COMMENTS.contains(node.getElementType())) {
        break;
      }
    }

    if (element instanceof GrDocCommentOwner) return (GrDocCommentOwner)element;
    return null;
  }

  @Nullable
  public static GrDocComment findDocComment(GrDocCommentOwner owner) {
    PsiElement element;
    if (owner instanceof GrVariable && owner.getParent() instanceof GrVariableDeclaration) {
      element = owner.getParent().getPrevSibling();
    }
    else {
      element = owner.getPrevSibling();
    }
    while (true) {
      if (element == null) return null;
      final ASTNode node = element.getNode();
      if (node == null) return null;
      if (GROOVY_DOC_COMMENT.equals(node.getElementType()) ||
          !WHITE_SPACES_OR_COMMENTS.contains(node.getElementType())) {
        break;
      }
      element = element.getPrevSibling();
    }
    if (element instanceof GrDocComment) return (GrDocComment)element;
    return null;
  }
}
