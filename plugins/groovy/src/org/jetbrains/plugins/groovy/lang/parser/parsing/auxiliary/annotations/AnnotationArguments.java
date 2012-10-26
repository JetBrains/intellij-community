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

    if (checkIdentAndAssign(builder)) {
      if (!parseAnnotationMemberValuePairs(builder, parser)) {
        annArgs.rollbackTo();
        return;
      }
    } else {
      PsiBuilder.Marker pairMarker = builder.mark();
      if (!parseAnnotationMemberValueInitializer(builder, parser)) {
        pairMarker.drop();
      } else {
        pairMarker.done(ANNOTATION_MEMBER_VALUE_PAIR);
      }
    }

    ParserUtils.getToken(builder, mNLS);

    if (!ParserUtils.getToken(builder, mRPAREN)) {
      builder.error(GroovyBundle.message("rparen.expected"));
    }
    annArgs.done(ANNOTATION_ARGUMENTS);
  }

  private static boolean checkIdentAndAssign(PsiBuilder builder) {
    //def is valid name identifier
    return ParserUtils.lookAhead(builder, mIDENT, mASSIGN) || ParserUtils.lookAhead(builder, kDEF, mASSIGN);
  }

  /*
  * annotationMemberValueInitializer ::=  conditionalExpression |	annotation
  */

  public static boolean parseAnnotationMemberValueInitializer(PsiBuilder builder, GroovyParser parser) {
    if (builder.getTokenType() == mAT) {
      return Annotation.parse(builder, parser);
    } else if(builder.getTokenType() == mLBRACK) {
      PsiBuilder.Marker marker = builder.mark();
      ParserUtils.getToken(builder, mLBRACK);
      while (parseAnnotationMemberValueInitializer(builder, parser)){
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

  private static boolean parseAnnotationMemberValuePairs(PsiBuilder builder, GroovyParser parser) {
    PsiBuilder.Marker start = builder.mark();

    if (!parseAnnotationMemberValueSinglePair(builder, parser)) {
      start.rollbackTo();
      return false;
    }

    while (ParserUtils.getToken(builder, mCOMMA)) {
      ParserUtils.getToken(builder, mNLS);

      if (!parseAnnotationMemberValueSinglePair(builder, parser)) {
        start.rollbackTo();
        return false;
      }
    }
    start.drop();

    return true;
  }

  /*
   * annotationMemberValuePair ::= IDENT ASSIGN nls annotationMemberValueInitializer
   */

  private static boolean parseAnnotationMemberValueSinglePair(PsiBuilder builder, GroovyParser parser) {
    PsiBuilder.Marker annmvp = builder.mark();

    if (!ParserUtils.getToken(builder, mIDENT) && !ParserUtils.getToken(builder, kDEF)) {
      annmvp.rollbackTo();
      return false;
    }

    if (!ParserUtils.getToken(builder, mASSIGN)) {
      annmvp.rollbackTo();
      return false;
    }

    ParserUtils.getToken(builder, mNLS);

    if (!parseAnnotationMemberValueInitializer(builder, parser)) {
      builder.error(GroovyBundle.message("annotation.member.value.initializer.expected"));
    }

    annmvp.done(ANNOTATION_MEMBER_VALUE_PAIR);
    return true;
  }
}
