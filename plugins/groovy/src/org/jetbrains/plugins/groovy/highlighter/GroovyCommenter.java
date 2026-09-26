// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.groovy.highlighter;

import com.intellij.lang.CodeDocumentationAwareCommenter;
import com.intellij.psi.PsiComment;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.groovydoc.parser.GroovyDocElementTypes;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;


public final class GroovyCommenter implements CodeDocumentationAwareCommenter {
  @Override
  public String getLineCommentPrefix() {
    return "//";
  }

  @Override
  public String getBlockCommentPrefix() {
    return "/*";
  }

  @Override
  public String getBlockCommentSuffix() {
    return "*/";
  }

  @Override
  public String getCommentedBlockCommentPrefix() {
    return null;
  }

  @Override
  public String getCommentedBlockCommentSuffix() {
    return null;
  }

  @Override
  public @Nullable IElementType getLineCommentTokenType() {
    return GroovyTokenTypes.mSL_COMMENT;
  }

  @Override
  public @Nullable IElementType getBlockCommentTokenType() {
    return GroovyTokenTypes.mML_COMMENT;
  }

  @Override
  public @Nullable IElementType getDocumentationCommentTokenType() {
    return GroovyDocElementTypes.GROOVY_DOC_COMMENT;
  }

  @Override
  public @Nullable String getDocumentationCommentPrefix() {
    return "/**";
  }

  @Override
  public @Nullable String getDocumentationCommentLinePrefix() {
    return "*";
  }

  @Override
  public @Nullable String getDocumentationCommentSuffix() {
    return "*/";
  }

  @Override
  public boolean isDocumentationComment(PsiComment element) {
    return element.getText().startsWith(getDocumentationCommentPrefix());
  }
}
