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

package org.jetbrains.plugins.groovy.lang.groovydoc.highlighter;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.highlighter.GroovySyntaxHighlighter;
import org.jetbrains.plugins.groovy.lang.groovydoc.lexer.GroovyDocElementTypeImpl;
import org.jetbrains.plugins.groovy.lang.groovydoc.lexer.GroovyDocLexer;
import org.jetbrains.plugins.groovy.lang.groovydoc.lexer.GroovyDocTokenTypes;

import java.util.HashMap;
import java.util.Map;

/**
 * @author ilyas
 */
public class GroovyDocSyntaxHighlighter extends SyntaxHighlighterBase {
  private static final Map<IElementType, TextAttributesKey> ATTRIBUTES = new HashMap<>();
  private static final IElementType mGDOC_COMMENT_CONTENT = new GroovyDocElementTypeImpl("GDOC_COMMENT_CONTENT");

  @Override
  @NotNull
  public Lexer getHighlightingLexer() {
    return new GroovyDocHighlightingLexer();
  }

  private static final TokenSet tGDOC_COMMENT_TAGS = TokenSet.create(
      GroovyDocTokenTypes.mGDOC_TAG_NAME
  );

  private static final TokenSet tGDOC_COMMENT_CONTENT = TokenSet.create(
      mGDOC_COMMENT_CONTENT
  );


  static {
    fillMap(ATTRIBUTES, tGDOC_COMMENT_CONTENT, GroovySyntaxHighlighter.DOC_COMMENT_CONTENT);
    fillMap(ATTRIBUTES, tGDOC_COMMENT_TAGS, GroovySyntaxHighlighter.DOC_COMMENT_TAG);
  }


  @Override
  @NotNull
  public TextAttributesKey[] getTokenHighlights(IElementType type) {
    return pack(ATTRIBUTES.get(type));
  }

  private static class GroovyDocHighlightingLexer extends GroovyDocLexer {
    @Override
    public IElementType getTokenType() {
      return super.getTokenType() == GroovyDocTokenTypes.mGDOC_TAG_NAME ? GroovyDocTokenTypes.mGDOC_TAG_NAME : super.getTokenType();
    }
  }
}
