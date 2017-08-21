/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.lang.PsiBuilderUtil;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.parameters.ParameterList;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @author ilyas
 */
public class OpenOrClosableBlock {

  public static boolean parseOpenBlock(PsiBuilder builder, GroovyParser parser) {
    if (builder.getTokenType() != GroovyTokenTypes.mLCURLY) {
      return false;
    }

    if (parser.parseDeep()) {
      parseOpenBlockDeep(builder, parser);
    } else {
      parseBlockShallow(builder, GroovyElementTypes.OPEN_BLOCK);
    }

    return true;
  }

  public static void parseOpenBlockDeep(PsiBuilder builder, GroovyParser parser) {
    PsiBuilder.Marker marker = builder.mark();
    builder.advanceLexer();
    ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
    parser.parseBlockBody(builder);
    if (!builder.eof() && builder.getTokenType() != GroovyTokenTypes.mRCURLY) {
      builder.error(GroovyBundle.message("statement.expected"));
      ParserUtils.skipCountingBraces(builder, GroovyParser.RCURLY_ONLY);
    }

    ParserUtils.getToken(builder, GroovyTokenTypes.mRCURLY, GroovyBundle.message("rcurly.expected"));
    marker.done(GroovyElementTypes.OPEN_BLOCK);
  }


  public static IElementType parseClosableBlock(PsiBuilder builder, GroovyParser parser) {
    assert builder.getTokenType() == GroovyTokenTypes.mLCURLY : builder.getTokenType();
    if (parser.parseDeep()) {
      parseClosableBlockDeep(builder, parser);
    } else {
      parseBlockShallow(builder, GroovyElementTypes.CLOSABLE_BLOCK);
    }
    return GroovyElementTypes.CLOSABLE_BLOCK;
  }

  public static void parseBlockShallow(PsiBuilder builder, IElementType blockType) {
    PsiBuilderUtil.parseBlockLazy(builder, GroovyTokenTypes.mLCURLY, GroovyTokenTypes.mRCURLY, blockType);
  }

  public static void parseClosableBlockDeep(PsiBuilder builder, GroovyParser parser) {
    assert builder.getTokenType() == GroovyTokenTypes.mLCURLY : builder.getTokenType();
    PsiBuilder.Marker marker = builder.mark();
    builder.advanceLexer();
    ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
    closableBlockParamsOpt(builder, parser);
    parser.parseBlockBody(builder);
    ParserUtils.getToken(builder, GroovyTokenTypes.mRCURLY, GroovyBundle.message("rcurly.expected"));
    marker.done(GroovyElementTypes.CLOSABLE_BLOCK);
  }


  private static void closableBlockParamsOpt(PsiBuilder builder, GroovyParser parser) {
    ParameterList.parse(builder, GroovyTokenTypes.mCLOSABLE_BLOCK_OP, parser);
    ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
    ParserUtils.getToken(builder, GroovyTokenTypes.mCLOSABLE_BLOCK_OP);
  }
}
