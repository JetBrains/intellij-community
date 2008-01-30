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

package org.jetbrains.plugins.groovy.lang.groovydoc.parser.parsing;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.groovy.lang.groovydoc.parser.GroovyDocElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.annotations.NonNls;

/**
 * @author ilyas
 */
public class GroovyDocParsing implements GroovyDocElementTypes {

  @NonNls
  private static final String SEE_TAG = "@see";
  @NonNls
  private static final String LINK_TAG = "@link";
  @NonNls
  private static final String LINKPLAIN_TAG = "@linkplain";
  @NonNls
  private static final String THROWS_TAG = "@throws";
  @NonNls
  private static final String EXCEPTION_TAG = "@exception";
  @NonNls
  private static final String PARAM_TAG = "@param";
  @NonNls
  private static final String VALUE_TAG = "@value";

  private boolean isInInlinedTag = false;
  private int myBraceCounter = 0;


  public boolean parse(PsiBuilder builder) {

    while (parseDataItem(builder)) ;
    if (builder.getTokenType() == mGDOC_COMMENT_END) {
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
    if (ParserUtils.lookAhead(builder, mGDOC_INLINE_TAG_START, mGDOC_TAG_NAME) && !isInInlinedTag) {
      isInInlinedTag = true;
      parseTag(builder);
    } else if (mGDOC_TAG_NAME == builder.getTokenType() && !isInInlinedTag) {
      parseTag(builder);
    } else {
      builder.advanceLexer();
    }
    return true;
  }

  private static boolean timeToEnd(PsiBuilder builder) {
    return builder.eof() || builder.getTokenType() == mGDOC_COMMENT_END;
  }

  private boolean parseTag(PsiBuilder builder) {
    PsiBuilder.Marker marker = builder.mark();
    if (isInInlinedTag) {
      ParserUtils.getToken(builder, mGDOC_INLINE_TAG_START);
    }
    assert builder.getTokenType() == mGDOC_TAG_NAME;
    String tagName = builder.getTokenText();
    builder.advanceLexer();

    // todo parse specific tags content with refholders
    while (!timeToEnd(builder)) {
      if (isInInlinedTag) {
        if (builder.getTokenType() == mGDOC_INLINE_TAG_START) {
          myBraceCounter++;
          builder.advanceLexer();
        } else if (builder.getTokenType() == mGDOC_INLINE_TAG_END) {
          if (myBraceCounter > 0) {
            myBraceCounter--;
            builder.advanceLexer();
          } else {
            builder.advanceLexer();
            isInInlinedTag = false;
            marker.done(GDOC_INLINED_TAG);
            return true;
          }
        } else {
          builder.advanceLexer();
        }
      } else if (ParserUtils.lookAhead(builder, mGDOC_INLINE_TAG_START, mGDOC_TAG_NAME)) {
        isInInlinedTag = true;
        parseTag(builder);
      } else if (mGDOC_TAG_NAME == builder.getTokenType()) {
        marker.done(GDOC_TAG);
        return true;
      } else {
        builder.advanceLexer();
      }
    }
    marker.done(isInInlinedTag ? GDOC_INLINED_TAG : GDOC_TAG);
    isInInlinedTag = false;
    return true;
  }


}
