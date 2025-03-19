// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.groovy.lang.groovydoc.parser.parsing;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.lang.groovydoc.lexer.GroovyDocTokenTypes;
import org.jetbrains.plugins.groovy.lang.groovydoc.parser.GroovyDocElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

public class GroovyDocParsing {


  enum RESULT {
    ERROR, METHOD, FIELD
  }

  private static final @NonNls String SEE_TAG = "@see";
  private static final @NonNls String LINK_TAG = "@link";
  private static final @NonNls String LINKPLAIN_TAG = "@linkplain";
  private static final @NonNls String THROWS_TAG = "@throws";
  private static final @NonNls String EXCEPTION_TAG = "@exception";
  private static final @NonNls String PARAM_TAG = "@param";
  private static final @NonNls String VALUE_TAG = "@value";

  private static final TokenSet REFERENCE_BEGIN = TokenSet.create(GroovyDocTokenTypes.mGDOC_TAG_VALUE_TOKEN,
                                                                  GroovyDocTokenTypes.mGDOC_TAG_VALUE_SHARP_TOKEN);

  private boolean isInInlinedTag = false;
  private int myBraceCounter = 0;


  public boolean parse(PsiBuilder builder) {

    while (parseDataItem(builder)){ /*do nothing*/}

    if (builder.getTokenType() == GroovyDocTokenTypes.mGDOC_COMMENT_END) {
      while (!builder.eof()) {
        builder.advanceLexer();
      }
    }
    return true;
  }

  /**
   * Parses doc comment at toplevel
   *
   * @param builder given builder
   * @return false in case of commnet end
   */
  private boolean parseDataItem(PsiBuilder builder) {
    if (timeToEnd(builder)) return false;
    if (ParserUtils.lookAhead(builder, GroovyDocTokenTypes.mGDOC_INLINE_TAG_START, GroovyDocTokenTypes.mGDOC_TAG_NAME) && !isInInlinedTag) {
      isInInlinedTag = true;
      parseTag(builder);
    } else if (GroovyDocTokenTypes.mGDOC_TAG_NAME == builder.getTokenType() && !isInInlinedTag) {
      parseTag(builder);
    } else {
      builder.advanceLexer();
    }
    return true;
  }

  private static boolean timeToEnd(PsiBuilder builder) {
    return builder.eof() || builder.getTokenType() == GroovyDocTokenTypes.mGDOC_COMMENT_END;
  }

  private boolean parseTag(PsiBuilder builder) {
    PsiBuilder.Marker marker = builder.mark();
    if (isInInlinedTag) {
      ParserUtils.getToken(builder, GroovyDocTokenTypes.mGDOC_INLINE_TAG_START);
    }
    assert builder.getTokenType() == GroovyDocTokenTypes.mGDOC_TAG_NAME;
    String tagName = builder.getTokenText();
    builder.advanceLexer();

    if (isInInlinedTag) {
      if (LINK_TAG.equals(tagName) || LINKPLAIN_TAG.equals(tagName) || VALUE_TAG.equals(tagName)) {
        parseSeeOrLinkTagReference(builder);
      }
    } else {
      if (THROWS_TAG.equals(tagName) || EXCEPTION_TAG.equals(tagName)) {
        parseReferenceOrType(builder);
      } else if (SEE_TAG.equals(tagName)) {
        parseSeeOrLinkTagReference(builder);
      } else if (PARAM_TAG.equals(tagName)) {
        parseParamTagReference(builder);
      }
    }


    while (!timeToEnd(builder)) {
      if (isInInlinedTag) {
        if (builder.getTokenType() == GroovyDocTokenTypes.mGDOC_INLINE_TAG_START) {
          myBraceCounter++;
          builder.advanceLexer();
        } else if (builder.getTokenType() == GroovyDocTokenTypes.mGDOC_INLINE_TAG_END) {
          if (myBraceCounter > 0) {
            myBraceCounter--;
            builder.advanceLexer();
          } else {
            builder.advanceLexer();
            isInInlinedTag = false;
            marker.done(GroovyDocElementTypes.GDOC_INLINED_TAG);
            return true;
          }
        } else {
          builder.advanceLexer();
        }
      } else if (ParserUtils.lookAhead(builder, GroovyDocTokenTypes.mGDOC_INLINE_TAG_START, GroovyDocTokenTypes.mGDOC_TAG_NAME)) {
        isInInlinedTag = true;
        parseTag(builder);
      } else if (GroovyDocTokenTypes.mGDOC_TAG_NAME == builder.getTokenType()) {
        marker.done(GroovyDocElementTypes.GDOC_TAG);
        return true;
      } else {
        builder.advanceLexer();
      }
    }
    marker.done(isInInlinedTag ? GroovyDocElementTypes.GDOC_INLINED_TAG : GroovyDocElementTypes.GDOC_TAG);
    isInInlinedTag = false;
    return true;
  }

  private static boolean parseParamTagReference(PsiBuilder builder) {
    PsiBuilder.Marker marker = builder.mark();
    if (GroovyDocTokenTypes.mGDOC_TAG_VALUE_TOKEN == builder.getTokenType()) {
      builder.advanceLexer();
      marker.done(GroovyDocElementTypes.GDOC_PARAM_REF);
      return true;
    }
    marker.drop();
    return false;
  }

  private static boolean parseSeeOrLinkTagReference(PsiBuilder builder) {
    IElementType type = builder.getTokenType();
    if (!REFERENCE_BEGIN.contains(type)) return false;
    PsiBuilder.Marker marker = builder.mark();
    if (GroovyDocTokenTypes.mGDOC_TAG_VALUE_TOKEN == type) {
      builder.advanceLexer();
    }
    if (GroovyDocTokenTypes.mGDOC_TAG_VALUE_SHARP_TOKEN == builder.getTokenType()) {
      builder.advanceLexer();
      RESULT result = parseFieldOrMethod(builder);
      if (result == RESULT.ERROR) {
        marker.drop();
      }
      else if (result == RESULT.METHOD) {
        marker.done(GroovyDocElementTypes.GDOC_METHOD_REF);
      }
      else {
        marker.done(GroovyDocElementTypes.GDOC_FIELD_REF);
      }
      return true;
    }
    marker.drop();
    return true;
  }

  private static RESULT parseFieldOrMethod(PsiBuilder builder) {
    if (builder.getTokenType() != GroovyDocTokenTypes.mGDOC_TAG_VALUE_TOKEN) return RESULT.ERROR;
    builder.advanceLexer();
    PsiBuilder.Marker params = builder.mark();
    if (GroovyDocTokenTypes.mGDOC_TAG_VALUE_LPAREN != builder.getTokenType()) {
      params.drop();
      return RESULT.FIELD;
    }
    builder.advanceLexer();
    while (parseMethodParameter(builder) && !timeToEnd(builder)) {
      while (GroovyDocTokenTypes.mGDOC_TAG_VALUE_COMMA != builder.getTokenType() &&
              GroovyDocTokenTypes.mGDOC_TAG_VALUE_RPAREN != builder.getTokenType() &&
              !timeToEnd(builder)) {
        builder.advanceLexer();
      }
      while (GroovyDocTokenTypes.mGDOC_TAG_VALUE_COMMA == builder.getTokenType()) {
        builder.advanceLexer();
      }
    }
    if (builder.getTokenType() == GroovyDocTokenTypes.mGDOC_TAG_VALUE_RPAREN) {
      builder.advanceLexer();
    }
    params.done(GroovyDocElementTypes.GDOC_METHOD_PARAMS);
    return RESULT.METHOD;
  }

  private static boolean parseMethodParameter(PsiBuilder builder) {
    PsiBuilder.Marker param = builder.mark();
    if (GroovyDocTokenTypes.mGDOC_TAG_VALUE_TOKEN == builder.getTokenType()) {
      builder.advanceLexer();
    } else {
      param.drop();
      return false;
    }

    if (GroovyDocTokenTypes.mGDOC_TAG_VALUE_TOKEN == builder.getTokenType()) {
      builder.advanceLexer();
    }
    param.done(GroovyDocElementTypes.GDOC_METHOD_PARAMETER);

    return true;
  }

  private static boolean parseReferenceOrType(PsiBuilder builder) {
    IElementType type = builder.getTokenType();
    if (GroovyDocTokenTypes.mGDOC_TAG_VALUE_TOKEN != type) return false;
    return true;
  }


}
