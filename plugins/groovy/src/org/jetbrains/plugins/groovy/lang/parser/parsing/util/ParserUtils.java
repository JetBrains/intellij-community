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

package org.jetbrains.plugins.groovy.lang.parser.parsing.util;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;

/**
 * Utility classdef, that contains various useful methods for
 * parser needs.
 *
 * @author ilyas
 */
public abstract class ParserUtils {

  /**
   * Auxiliary method for strict token appearance
   *
   * @param builder  current builder
   * @param elem     given element
   * @param errorMsg Message, that displays if element was not found; if errorMsg == null nothing displays
   * @return true if element parsed
   */
  public static boolean getToken(PsiBuilder builder, IElementType elem, String errorMsg) {
    if (elem.equals(builder.getTokenType())) {
      builder.advanceLexer();
      return true;
    } else {
      if (errorMsg != null)
        builder.error(errorMsg);
      return false;
    }
  }

  /**
   * Auxiliary method for construction like
   * <BNF>
   * token?
   * </BNF>
   * parsing
   *
   * @param builder current builder
   * @param elem    given element
   * @return true if element parsed
   */
  public static boolean getToken(PsiBuilder builder, IElementType elem) {
    if (elem.equals(builder.getTokenType())) {
      builder.advanceLexer();
      return true;
    }
    return false;
  }

  /**
   * Same as simple getToken() method but with TokenSet
   *
   * @param builder
   * @param tokenSet
   * @return
   */
  public static boolean getToken(PsiBuilder builder, TokenSet tokenSet) {
    if (tokenSet.contains(builder.getTokenType())) {
      return getToken(builder, builder.getTokenType(), null);
    }
    return false;
  }

  /**
   * Same as simple getToken() method but with TokenSet
   *
   * @param builder
   * @param tokenSet
   * @return
   */
  public static boolean getToken(PsiBuilder builder, TokenSet tokenSet, String msg) {
    if (tokenSet.contains(builder.getTokenType())) {
      return getToken(builder, builder.getTokenType(), msg);
    }
    return false;
  }

  /**
   * Checks, that following element sequence is like given
   *
   * @param builder Given PsiBuilder
   * @param elems   Array of need elements in order
   * @return true if following sequence is like a given
   */
  public static boolean lookAhead(PsiBuilder builder, IElementType... elems) {
    if (!elems[0].equals(builder.getTokenType())) return false;

    if (elems.length == 1) return true;

    Marker rb = builder.mark();
    builder.advanceLexer();
    int i = 1;
    while (!builder.eof() && i < elems.length && elems[i].equals(builder.getTokenType())) {
      builder.advanceLexer();
      i++;
    }
    rb.rollbackTo();
    return i == elems.length;
  }

  /**
   * Wraps current token to node with specified element type
   *
   * @param builder Given builder
   * @param elem    Node element
   * @return elem type
   */
  public static IElementType eatElement(PsiBuilder builder, IElementType elem) {
    Marker marker = builder.mark();
    builder.advanceLexer();
    marker.done(elem);
    return elem;
  }

  /**
   * Wraps current token with error message
   *
   * @param builder
   * @param msg     Error message
   */
  public static void wrapError(PsiBuilder builder, String msg) {
    Marker marker = builder.mark();
    builder.advanceLexer();
    marker.error(msg);
  }

  @Deprecated
  public static void advance(PsiBuilder builder, int count) {
    for (int i = 0; i < count; i++) {
      builder.getTokenText();
      builder.advanceLexer();
    }
  }

  @Deprecated
  public static void advance(PsiBuilder builder) {
    advance(builder, 1);
  }

  public static boolean skipCountingBraces(PsiBuilder builder, TokenSet until) {
    int braceLevel = 0;
    while (true) {
      if (builder.eof()) {
        return false;
      }
      final IElementType type = builder.getTokenType();
      if (braceLevel == 0 && until.contains(type)) {
        return true;
      }

      if (GroovyTokenTypes.mLCURLY == type) {
        braceLevel++;
      }
      else if (GroovyTokenTypes.mRCURLY == type) {
        braceLevel--;
      }
      builder.advanceLexer();
    }
  }


}
