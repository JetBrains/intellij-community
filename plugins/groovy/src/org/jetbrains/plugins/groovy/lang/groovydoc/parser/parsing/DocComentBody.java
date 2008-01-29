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

/**
 * @author ilyas
 */
public class DocComentBody implements GroovyDocElementTypes{

  public static boolean parse(PsiBuilder builder) {
    if (builder.getTokenType() == mGDOC_COMMENT_END) {
      while (!builder.eof()) {
        builder.advanceLexer();
      }
      return true;
    }
    while (builder.getTokenType() != mGDOC_TAG_NAME &&
        !builder.eof()) {
      builder.advanceLexer();
      // todo parse inline tags
    }
    while (parseDocCommentTag(builder));
    while (!builder.eof()) {
      builder.advanceLexer();
    }
    return true;
  }

  private static boolean parseDocCommentTag(PsiBuilder builder) {
    PsiBuilder.Marker marker = builder.mark();
    if (!ParserUtils.getToken(builder, mGDOC_TAG_NAME)) {
      marker.drop();
      return false;
    }
    IElementType type = builder.getTokenType();
    while (!builder.eof() &&
        type != mGDOC_COMMENT_END &&
        type != mGDOC_TAG_NAME) {

      // todo parse specific tag content
      builder.advanceLexer();
      type = builder.getTokenType();
    }
    marker.done(GDOC_TAG);
    return true;
  }
}
