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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.blocks.OpenOrClosableBlock;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.StrictContextExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @author ilyas
 */
public class SynchronizedStatement implements GroovyElementTypes {
  public static GroovyElementType parse(PsiBuilder builder) {

    PsiBuilder.Marker marker = builder.mark();

    ParserUtils.getToken(builder, kSYNCHRONIZED);

    if (!ParserUtils.getToken(builder, mLPAREN, GroovyBundle.message("lparen.expected"))) {
      marker.drop();
      return WRONGWAY;
    }

    if (StrictContextExpression.parse(builder).equals(WRONGWAY)) {
      builder.error(GroovyBundle.message("expression.expected"));
    }

    ParserUtils.getToken(builder, mNLS);
    if (!ParserUtils.getToken(builder, mRPAREN, GroovyBundle.message("rparen.expected"))) {
      while (!builder.eof() && !mNLS.equals(builder.getTokenType()) && !mRPAREN.equals(builder.getTokenType())) {
        builder.advanceLexer();
        builder.error(GroovyBundle.message("rparen.expected"));
      }
      if (!ParserUtils.getToken(builder, mRPAREN)) {
        marker.done(SYNCHRONIZED_STATEMENT);
        return SYNCHRONIZED_STATEMENT;
      }
    }

    PsiBuilder.Marker warn = builder.mark();
    if (builder.getTokenType() == mNLS) {
      ParserUtils.getToken(builder, mNLS);
    }

    GroovyElementType result = WRONGWAY;
    if (mLCURLY.equals(builder.getTokenType())) {
      result = OpenOrClosableBlock.parseOpenBlock(builder);
    }
    if (result.equals(WRONGWAY) || result.equals(IMPORT_STATEMENT)) {
      warn.rollbackTo();
      builder.error(GroovyBundle.message("block.expression.expected"));
      marker.done(SYNCHRONIZED_STATEMENT);
      return SYNCHRONIZED_STATEMENT;
    } else {
      warn.drop();
      marker.done(SYNCHRONIZED_STATEMENT);
      return SYNCHRONIZED_STATEMENT;
    }

  }
}
