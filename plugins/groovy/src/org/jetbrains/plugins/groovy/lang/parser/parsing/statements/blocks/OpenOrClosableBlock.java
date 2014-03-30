/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.blocks;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.parameters.ParameterList;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @author ilyas
 */
public class OpenOrClosableBlock implements GroovyElementTypes {

  public static boolean parseOpenBlock(PsiBuilder builder, GroovyParser parser) {
    if (builder.getTokenType() != mLCURLY) {
      return false;
    }

    if (parser.parseDeep()) {
      parseOpenBlockDeep(builder, parser);
    } else {
      parseBlockShallow(builder, OPEN_BLOCK);
    }

    return true;
  }

  public static void parseOpenBlockDeep(PsiBuilder builder, GroovyParser parser) {
    PsiBuilder.Marker marker = builder.mark();
    builder.advanceLexer();
    ParserUtils.getToken(builder, mNLS);
    parser.parseBlockBody(builder);
    if (!builder.eof() && builder.getTokenType() != mRCURLY) {
      builder.error(GroovyBundle.message("statement.expected"));
      ParserUtils.skipCountingBraces(builder, GroovyParser.RCURLY_ONLY);
    }

    ParserUtils.getToken(builder, mRCURLY, GroovyBundle.message("rcurly.expected"));
    marker.done(OPEN_BLOCK);
  }


  public static IElementType parseClosableBlock(PsiBuilder builder, GroovyParser parser) {
    assert builder.getTokenType() == mLCURLY : builder.getTokenType();
    if (parser.parseDeep()) {
      parseClosableBlockDeep(builder, parser);
    } else {
      parseBlockShallow(builder, CLOSABLE_BLOCK);
    }
    return CLOSABLE_BLOCK;
  }

  public static void parseBlockShallow(PsiBuilder builder, IElementType blockType) {
    final PsiBuilder.Marker blockStart = builder.mark();
    int braceCount = 0;
    while (true) {
      final IElementType tokenType = builder.getTokenType();
      if (tokenType == null) {
        break;
      }

      if (tokenType == mLCURLY) {
        braceCount++;
      } else if (tokenType == mRCURLY) {
        braceCount--;
      }
      builder.advanceLexer();
      if (braceCount == 0) {
        break;
      }
    }
    blockStart.collapse(blockType);
  }

  public static void parseClosableBlockDeep(PsiBuilder builder, GroovyParser parser) {
    assert builder.getTokenType() == mLCURLY : builder.getTokenType();
    PsiBuilder.Marker marker = builder.mark();
    builder.advanceLexer();
    ParserUtils.getToken(builder, mNLS);
    closableBlockParamsOpt(builder, parser);
    parser.parseBlockBody(builder);
    ParserUtils.getToken(builder, mRCURLY, GroovyBundle.message("rcurly.expected"));
    marker.done(CLOSABLE_BLOCK);
  }


  private static void closableBlockParamsOpt(PsiBuilder builder, GroovyParser parser) {
    ParameterList.parse(builder, mCLOSABLE_BLOCK_OP, parser);
    ParserUtils.getToken(builder, mNLS);
    ParserUtils.getToken(builder, mCLOSABLE_BLOCK_OP);
  }

}
