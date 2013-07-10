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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.primary;

import com.intellij.lang.PsiBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.blocks.OpenOrClosableBlock;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arithmetic.PathExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @author ilyas
 */
public class CompoundStringExpression implements GroovyElementTypes {
  private static final Logger LOG = Logger.getInstance(CompoundStringExpression.class);

  /**
   * Groovy lexer does not smart enough to understand whether a regex contents injections or not. So the parser should do this job.
   * We create additional marker2 for the case of absence of injections. In this case resulting tree is as follows:
   *
   * Regex
   *   mRegexLiteral    (mDollarSlashRegexLiteral)
   *     mRegexBegin    (........................)
   *     mRegexContent  (........................)
   *     mRegexEnd      (........................)
   *
   * This tree emulates tree of simple GrLiteralImpl structure so we can use regexes where simple strings are expected.
   *
   * @return true if there are any injections
   */
  public static boolean parse(PsiBuilder builder,
                              GroovyParser parser,
                              boolean forRefExpr,
                              IElementType begin,
                              IElementType content,
                              IElementType end,
                              @Nullable IElementType literal,
                              GroovyElementType compoundLiteral, String message) {
    PsiBuilder.Marker marker = builder.mark();
    final PsiBuilder.Marker marker2 = builder.mark();
    LOG.assertTrue(ParserUtils.getToken(builder, begin));

    if (builder.getTokenType() == content) {
      final PsiBuilder.Marker contentMarker = builder.mark();
      builder.advanceLexer();
      if (builder.getTokenType() == mDOLLAR || literal == null) {
        contentMarker.done(GSTRING_CONTENT);
      }
      else {
        contentMarker.drop();
      }
    }

    boolean inj = builder.getTokenType() == mDOLLAR;
    while (builder.getTokenType() == mDOLLAR || builder.getTokenType() == content) {
      if (builder.getTokenType() == mDOLLAR) {
        parseInjection(builder, parser);
      }
      else {
        ParserUtils.eatElement(builder, GSTRING_CONTENT);
      }
    }

    if (!ParserUtils.getToken(builder, end)) {
      builder.error(message);
    }

    if (inj || literal == null) {
      marker2.drop();
      marker.done(compoundLiteral);
    }
    else {
      marker2.done(literal);
      if (forRefExpr) {
        marker.drop();
      }
      else {
        marker.done(LITERAL);
      }
    }
    return inj;
  }

  /**
   * Parses heredoc's content in GString
   *
   * @param builder given builder
   * @return nothing
   */
  private static boolean parseInjection(PsiBuilder builder, GroovyParser parser) {
    if (builder.getTokenType() != mDOLLAR) return false;

    final PsiBuilder.Marker injection = builder.mark();
    ParserUtils.getToken(builder, mDOLLAR);

    if (mIDENT.equals(builder.getTokenType())) {
      PathExpression.parse(builder, parser);
    }
    else if (mLCURLY.equals(builder.getTokenType())) {
      OpenOrClosableBlock.parseClosableBlock(builder, parser);
    }
    else {
      ParserUtils.wrapError(builder, GroovyBundle.message("identifier.or.block.expected"));
    }

    injection.done(GSTRING_INJECTION);
    return true;
  }
}