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
import org.jetbrains.annotations.NotNull;
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
  private final PsiBuilder myBuilder;
  private final GroovyParser myParser;
  private final boolean myForRefExpr;
  private final IElementType myBegin;
  private final IElementType myContent;
  private final IElementType myEnd;
  private final IElementType mySimpleLiteral;
  private final GroovyElementType myCompoundLiteral;
  private final String myMessage;

  private CompoundStringExpression(PsiBuilder builder,
                                  GroovyParser parser,
                                  boolean forRefExpr,
                                  IElementType begin,
                                  IElementType content,
                                  IElementType end,
                                  IElementType literal,
                                  GroovyElementType compoundLiteral,
                                  String message) {

    myBuilder = builder;
    myParser = parser;
    myForRefExpr = forRefExpr;
    myBegin = begin;
    myContent = content;
    myEnd = end;
    mySimpleLiteral = literal;
    myCompoundLiteral = compoundLiteral;
    myMessage = message;
  }

  private boolean parse() {
    PsiBuilder.Marker marker = myBuilder.mark();
    final PsiBuilder.Marker marker2 = myBuilder.mark();
    LOG.assertTrue(ParserUtils.getToken(myBuilder, myBegin));


    if (mySimpleLiteral != null && myBuilder.getTokenType() == myEnd) {
      myBuilder.advanceLexer();
      finishSimpleLiteral(marker, marker2);
      return true;
    }

    if (myBuilder.getTokenType() == myContent) {
      final PsiBuilder.Marker contentMarker = myBuilder.mark();
      myBuilder.advanceLexer();
      if (myBuilder.getTokenType() == mDOLLAR || mySimpleLiteral == null) {
        contentMarker.done(GSTRING_CONTENT);
      }
      else {
        contentMarker.drop();
      }
    }
    else {
      processContent();
    }

    boolean hasInjection = myBuilder.getTokenType() == mDOLLAR;
    while (myBuilder.getTokenType() == mDOLLAR) {
      parseInjection();
      processContent();
    }

    if (!ParserUtils.getToken(myBuilder, myEnd)) {
      myBuilder.error(myMessage);
    }

    if (hasInjection || mySimpleLiteral == null) {
      marker2.drop();
      marker.done(myCompoundLiteral);
    }
    else {
      finishSimpleLiteral(marker, marker2);
    }
    return hasInjection;
  }

  private void processContent() {
    PsiBuilder.Marker marker = myBuilder.mark();
    if (myBuilder.getTokenType() == myContent) {
      myBuilder.advanceLexer();
    }
    else {
      myBuilder.mark().done(myContent);
    }
    marker.done(GSTRING_CONTENT);
  }

  private void finishSimpleLiteral(PsiBuilder.Marker marker, PsiBuilder.Marker marker2) {
    marker2.done(mySimpleLiteral);
    if (myForRefExpr) {
      marker.drop();
    }
    else {
      marker.done(LITERAL);
    }
  }

  /**
   * Parses heredoc's content in GString
   *
   * @return nothing
   */
  private boolean parseInjection() {
    if (myBuilder.getTokenType() != mDOLLAR) return false;

    final PsiBuilder.Marker injection = myBuilder.mark();
    ParserUtils.getToken(myBuilder, mDOLLAR);

    if (myBuilder.getTokenType() == mIDENT || myBuilder.getTokenType() == kTHIS) {
      PathExpression.parse(myBuilder, myParser);
    }
    else if (myBuilder.getTokenType() == mLCURLY) {
      OpenOrClosableBlock.parseClosableBlock(myBuilder, myParser);
    }
    else {
      ParserUtils.wrapError(myBuilder, GroovyBundle.message("identifier.or.block.expected"));
    }

    injection.done(GSTRING_INJECTION);
    return true;
  }

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
  public static boolean parse(@NotNull PsiBuilder builder,
                              @NotNull GroovyParser parser,
                              boolean forRefExpr,
                              @NotNull IElementType begin,
                              @NotNull IElementType content,
                              @NotNull IElementType end,
                              @Nullable IElementType literal,
                              @NotNull GroovyElementType compoundLiteral,
                              @NotNull String message) {
    return new CompoundStringExpression(builder, parser, forRefExpr, begin, content, end, literal, compoundLiteral, message).parse();
  }
}