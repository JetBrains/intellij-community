// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.groovydoc.lexer;

import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.plugins.groovy.lang.groovydoc.parser.elements.GroovyDocTagValueTokenType;

/**
 * @author ilyas
 */
public interface GroovyDocTokenTypes {

  IElementType mGDOC_TAG_VALUE_TOKEN = new GroovyDocTagValueTokenType();

  IElementType mGDOC_COMMENT_START = new GroovyDocElementType("GDOC_COMMENT_START");
  IElementType mGDOC_COMMENT_END = new GroovyDocElementType("GDOC_COMMENT_END");

  IElementType mGDOC_COMMENT_DATA = new GroovyDocElementType("GDOC_COMMENT_DATA");
  IElementType mGDOC_TAG_NAME = new GroovyDocElementType("GDOC_TAG_NAME");
  IElementType mGDOC_WHITESPACE = new GroovyDocElementType("GDOC_WHITESPACE");
  IElementType mGDOC_TAG_PLAIN_VALUE_TOKEN = new GroovyDocElementType("GDOC_TAG_VALUE_TOKEN");
  IElementType mGDOC_TAG_VALUE_LPAREN = new GroovyDocElementType("GDOC_TAG_VALUE_LPAREN");
  IElementType mGDOC_TAG_VALUE_RPAREN = new GroovyDocElementType("GDOC_TAG_VALUE_RPAREN");
  IElementType mGDOC_INLINE_TAG_END = new GroovyDocElementType("GDOC_INLINE_TAG_END");
  IElementType mGDOC_INLINE_TAG_START = new GroovyDocElementType("DOC_INLINE_TAG_START");
  IElementType mGDOC_TAG_VALUE_COMMA = new GroovyDocElementType("GDOC_TAG_VALUE_COMMA");
  IElementType mGDOC_TAG_VALUE_SHARP_TOKEN = new GroovyDocElementType("GDOC_TAG_VALUE_SHARP_TOKEN");
  IElementType mGDOC_ASTERISKS = new GroovyDocElementType("GDOC_LEADING_ASTERISKS");

  IElementType mGDOC_COMMENT_BAD_CHARACTER = TokenType.BAD_CHARACTER;

  TokenSet GROOVY_DOC_TOKENS = TokenSet.create(
      mGDOC_COMMENT_START,
      mGDOC_COMMENT_END,
      mGDOC_COMMENT_DATA,
      mGDOC_TAG_NAME,
//      mGDOC_WHITESPACE,
      mGDOC_TAG_VALUE_TOKEN,
      mGDOC_TAG_VALUE_LPAREN,
      mGDOC_TAG_VALUE_RPAREN,
      mGDOC_INLINE_TAG_END,
      mGDOC_INLINE_TAG_START,
      mGDOC_TAG_VALUE_COMMA,
      mGDOC_TAG_VALUE_SHARP_TOKEN,
      mGDOC_ASTERISKS,
      mGDOC_COMMENT_BAD_CHARACTER
  );

}
