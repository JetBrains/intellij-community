/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.groovydoc.lexer;

import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.groovy.lang.groovydoc.parser.GroovyDocElementTypes;

/**
 * @author ilyas
 */
public interface GroovyDocTokenTypes {

  IElementType mGDOC_COMMENT_START = new GroovyDocElementType("GDOC_COMMENT_START");
  IElementType mGDOC_COMMENT_END = new GroovyDocElementType("GDOC_COMMENT_END");

  IElementType mGDOC_COMMENT_DATA = new GroovyDocElementType("GDOC_COMMENT_DATA");
  IElementType mGDOC_TAG_NAME = new GroovyDocElementType("GDOC_TAG_NAME");
  IElementType mGDOC_WHITESPACE = new GroovyDocElementType("GDOC_WHITESPACE");
  IElementType mGDOC_TAG_VALUE_TOKEN = new GroovyDocElementType("GDOC_TAG_VALUE_TOKEN");
  IElementType mGDOC_TAG_VALUE_LPAREN = new GroovyDocElementType("GDOC_TAG_VALUE_LPAREN");
  IElementType mGDOC_TAG_VALUE_RPAREN = new GroovyDocElementType("GDOC_TAG_VALUE_RPAREN");
  IElementType mGDOC_TAG_VALUE_GT = new GroovyDocElementType("GDOC_TAG_VALUE_GT");
  IElementType mGDOC_TAG_VALUE_LT = new GroovyDocElementType("GDOC_TAG_VALUE_LT");
  IElementType mGDOC_INLINE_TAG_END = new GroovyDocElementType("GDOC_INLINE_TAG_END");
  IElementType mGDOC_INLINE_TAG_START = new GroovyDocElementType("DOC_INLINE_TAG_START");
  IElementType mGDOC_TAG_VALUE_COMMA = new GroovyDocElementType("GDOC_TAG_VALUE_COMMA");
  IElementType mGDOC_TAG_VALUE_SHARP_TOKEN = new GroovyDocElementType("GDOC_TAG_VALUE_SHARP_TOKEN");
  IElementType mGDOC_ASTERISKS = new GroovyDocElementType("GDOC_LEADING_ASTERISKS");

  IElementType mGDOC_COMMENT_BAD_CHARACTER = new GroovyDocElementType("DOC_COMMENT_BAD_CHARACTER");
}
