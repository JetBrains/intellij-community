// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  IElementType mGDOC_COMMENT_START = new GroovyDocElementTypeImpl("GDOC_COMMENT_START");
  IElementType mGDOC_COMMENT_END = new GroovyDocElementTypeImpl("GDOC_COMMENT_END");

  IElementType mGDOC_COMMENT_DATA = new GroovyDocElementTypeImpl("GDOC_COMMENT_DATA");
  IElementType mGDOC_TAG_NAME = new GroovyDocElementTypeImpl("GDOC_TAG_NAME");
  IElementType mGDOC_WHITESPACE = new GroovyDocElementTypeImpl("GDOC_WHITESPACE");
  IElementType mGDOC_TAG_PLAIN_VALUE_TOKEN = new GroovyDocElementTypeImpl("GDOC_TAG_VALUE_TOKEN");
  IElementType mGDOC_TAG_VALUE_LPAREN = new GroovyDocElementTypeImpl("GDOC_TAG_VALUE_LPAREN");
  IElementType mGDOC_TAG_VALUE_RPAREN = new GroovyDocElementTypeImpl("GDOC_TAG_VALUE_RPAREN");
  IElementType mGDOC_TAG_VALUE_GT = new GroovyDocElementTypeImpl("GDOC_TAG_VALUE_GT");
  IElementType mGDOC_TAG_VALUE_LT = new GroovyDocElementTypeImpl("GDOC_TAG_VALUE_LT");
  IElementType mGDOC_INLINE_TAG_END = new GroovyDocElementTypeImpl("GDOC_INLINE_TAG_END");
  IElementType mGDOC_INLINE_TAG_START = new GroovyDocElementTypeImpl("DOC_INLINE_TAG_START");
  IElementType mGDOC_TAG_VALUE_COMMA = new GroovyDocElementTypeImpl("GDOC_TAG_VALUE_COMMA");
  IElementType mGDOC_TAG_VALUE_SHARP_TOKEN = new GroovyDocElementTypeImpl("GDOC_TAG_VALUE_SHARP_TOKEN");
  IElementType mGDOC_ASTERISKS = new GroovyDocElementTypeImpl("GDOC_LEADING_ASTERISKS");

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
      mGDOC_TAG_VALUE_GT,
      mGDOC_TAG_VALUE_LT,
      mGDOC_INLINE_TAG_END,
      mGDOC_INLINE_TAG_START,
      mGDOC_TAG_VALUE_COMMA,
      mGDOC_TAG_VALUE_SHARP_TOKEN,
      mGDOC_ASTERISKS,
      mGDOC_COMMENT_BAD_CHARACTER
  );

}
