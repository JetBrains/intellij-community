/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.blocks.OpenOrClosableBlock;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.ExpressionStatement;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @author ilyas
 */
public class SynchronizedStatement {
  public static boolean parse(PsiBuilder builder, GroovyParser parser) {

    PsiBuilder.Marker marker = builder.mark();

    ParserUtils.getToken(builder, GroovyTokenTypes.kSYNCHRONIZED);

    if (!ParserUtils.getToken(builder, GroovyTokenTypes.mLPAREN, GroovyBundle.message("lparen.expected"))) {
      marker.drop();
      return false;
    }

    if (!ExpressionStatement.argParse(builder, parser)) {
      builder.error(GroovyBundle.message("expression.expected"));
    }

    ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
    if (!ParserUtils.getToken(builder, GroovyTokenTypes.mRPAREN, GroovyBundle.message("rparen.expected"))) {
      marker.done(GroovyElementTypes.SYNCHRONIZED_STATEMENT);
      return true;
    }

    PsiBuilder.Marker warn = builder.mark();
    ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);

    if (GroovyTokenTypes.mLCURLY.equals(builder.getTokenType()) && !OpenOrClosableBlock.parseOpenBlock(builder, parser)) {
      warn.rollbackTo();
      builder.error(GroovyBundle.message("block.expression.expected"));
    } else {
      warn.drop();
    }
    marker.done(GroovyElementTypes.SYNCHRONIZED_STATEMENT);

    return true;
  }
}
