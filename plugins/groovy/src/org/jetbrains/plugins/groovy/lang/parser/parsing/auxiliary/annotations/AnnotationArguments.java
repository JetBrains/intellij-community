/*
 * Copyright 2000-2009 JetBrains s.r.o.
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


public class AnnotationArguments implements GroovyElementTypes {
  public static void parse(PsiBuilder builder, GroovyParser parser) {

    PsiBuilder.Marker annArgs = builder.mark();
    if (!ParserUtils.getToken(builder, mLPAREN)) {
      annArgs.done(ANNOTATION_ARGUMENTS);
      return;
    }

    if (ParserUtils.lookAhead(builder, mIDENT, mASSIGN)) {
      parsePairs(builder, parser);
    }
    else {
      PsiBuilder.Marker pairMarker = builder.mark();
      if (parseAnnotationMemberValueInitializer(builder, parser)) {
        pairMarker.done(ANNOTATION_MEMBER_VALUE_PAIR);
      }
      else {
        pairMarker.drop();
      }
    }

    ParserUtils.getToken(builder, mNLS);
    ParserUtils.getToken(builder, mRPAREN, GroovyBundle.message("rparen.expected"));
    annArgs.done(ANNOTATION_ARGUMENTS);
  }

  /*
  * annotationMemberValueInitializer ::=  conditionalExpression |	annotation
  */

  public static boolean parseAnnotationMemberValueInitializer(PsiBuilder builder, GroovyParser parser) {
    if (builder.getTokenType() == mAT) {
      return Annotation.parse(builder, parser);
    }
    else if (builder.getTokenType() == mLBRACK) {
      PsiBuilder.Marker marker = builder.mark();
      ParserUtils.getToken(builder, mLBRACK);
      while (parseAnnotationMemberValueInitializer(builder, parser)) {
        if (builder.eof() || builder.getTokenType() == mRBRACK) break;
        ParserUtils.getToken(builder, mCOMMA, GroovyBundle.message("comma.expected"));
      }

      ParserUtils.getToken(builder, mRBRACK, GroovyBundle.message("rbrack.expected"));
      marker.done(ANNOTATION_ARRAY_INITIALIZER);
      return true;
    }

    //check
    return ConditionalExpression.parse(builder, parser) && !ParserUtils.getToken(builder, mASSIGN);
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

    while (ParserUtils.getToken(builder, mCOMMA)) {
      ParserUtils.getToken(builder, mNLS);

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

    if (ParserUtils.lookAhead(builder, mIDENT, mASSIGN)) {
      ParserUtils.getToken(builder, mIDENT);
      ParserUtils.getToken(builder, mASSIGN);
      ParserUtils.getToken(builder, mNLS);
    }
    else {
      builder.error(GroovyBundle.message("attribute.name.expected"));
    }

    if (!parseAnnotationMemberValueInitializer(builder, parser)) {
      builder.error(GroovyBundle.message("annotation.member.value.initializer.expected"));
    }

    marker.done(ANNOTATION_MEMBER_VALUE_PAIR);
    return true;
  }
}
