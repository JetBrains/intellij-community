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

package org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.annotations;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.ConditionalExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 03.04.2007
 */

/*
 * annotationArguments ::= annotationMemberValueInitializer |	anntotationMemberValuePairs
 */


public class AnnotationArguments {
  public static void parse(PsiBuilder builder, GroovyParser parser) {

    PsiBuilder.Marker annArgs = builder.mark();
    if (!ParserUtils.getToken(builder, GroovyTokenTypes.mLPAREN)) {
      annArgs.done(GroovyElementTypes.ANNOTATION_ARGUMENTS);
      return;
    }

    if (builder.getTokenType() != GroovyTokenTypes.mRPAREN) {
      parsePairs(builder, parser);
    }

    ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
    ParserUtils.getToken(builder, GroovyTokenTypes.mRPAREN, GroovyBundle.message("rparen.expected"));
    annArgs.done(GroovyElementTypes.ANNOTATION_ARGUMENTS);
  }

  private static boolean checkIdentAndAssign(PsiBuilder builder) {
    final PsiBuilder.Marker marker = builder.mark();
    boolean result = ParserUtils.getToken(builder, TokenSets.CODE_REFERENCE_ELEMENT_NAME_TOKENS) && ParserUtils.getToken(builder,
                                                                                                                         GroovyTokenTypes.mASSIGN);
    marker.rollbackTo();
    return result;
  }

  /*
  * annotationMemberValueInitializer ::=  conditionalExpression |	annotation
  */

  public static boolean parseAnnotationMemberValueInitializer(PsiBuilder builder, GroovyParser parser) {
    if (builder.getTokenType() == GroovyTokenTypes.mAT) {
      return Annotation.parse(builder, parser);
    }
    else if (builder.getTokenType() == GroovyTokenTypes.mLBRACK) {
      PsiBuilder.Marker marker = builder.mark();
      ParserUtils.getToken(builder, GroovyTokenTypes.mLBRACK);
      while (parseAnnotationMemberValueInitializer(builder, parser)) {
        if (builder.eof() || builder.getTokenType() == GroovyTokenTypes.mRBRACK) break;
        ParserUtils.getToken(builder, GroovyTokenTypes.mCOMMA, GroovyBundle.message("comma.expected"));
      }

      ParserUtils.getToken(builder, GroovyTokenTypes.mRBRACK, GroovyBundle.message("rbrack.expected"));
      marker.done(GroovyElementTypes.ANNOTATION_ARRAY_INITIALIZER);
      return true;
    }

    //check
    return ConditionalExpression.parse(builder, parser) && !ParserUtils.getToken(builder, GroovyTokenTypes.mASSIGN);
  }

  /*
   * anntotationMemberValuePairs ::= annotationMemberValuePair ( COMMA nls annotationMemberValuePair )*
   */

  private static boolean parsePairs(PsiBuilder builder, GroovyParser parser) {
    PsiBuilder.Marker start = builder.mark();

    if (!parsePair(builder, parser)) {
      start.rollbackTo();
      return false;
    }

    while (ParserUtils.getToken(builder, GroovyTokenTypes.mCOMMA)) {
      ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);

      parsePair(builder, parser);
    }
    start.drop();

    return true;
  }

  /*
   * annotationMemberValuePair ::= IDENT ASSIGN nls annotationMemberValueInitializer
   */

  private static boolean parsePair(PsiBuilder builder, GroovyParser parser) {
    PsiBuilder.Marker marker = builder.mark();

    final PsiBuilder.Marker lfMarker;
    if (checkIdentAndAssign(builder)) {
      ParserUtils.getToken(builder, TokenSets.CODE_REFERENCE_ELEMENT_NAME_TOKENS);
      ParserUtils.getToken(builder, GroovyTokenTypes.mASSIGN);

      lfMarker = builder.mark();
      ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
    }
    else {
      lfMarker = null;
    }

    if (!parseAnnotationMemberValueInitializer(builder, parser)) {
      if (lfMarker != null) {
        lfMarker.rollbackTo();
        builder.error(GroovyBundle.message("annotation.member.value.initializer.expected"));
      }
      else {
        builder.error(GroovyBundle.message("annotation.attribute.expected"));
      }
    }
    else if (lfMarker != null) {
      lfMarker.drop();
    }

    marker.done(GroovyElementTypes.ANNOTATION_MEMBER_VALUE_PAIR);
    return true;
  }
}
