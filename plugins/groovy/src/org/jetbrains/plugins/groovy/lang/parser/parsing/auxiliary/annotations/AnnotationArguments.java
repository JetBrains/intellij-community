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

package org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.annotations;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
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
  public static void parse(PsiBuilder builder) {

    PsiBuilder.Marker annArgs = builder.mark();
    if (!ParserUtils.getToken(builder, mLPAREN)) {
      annArgs.done(ANNOTATION_ARGUMENTS);
      return;
    }

    final PsiBuilder.Marker marker = builder.mark();
    if (!parseAnnotationMemberValueInitializer(builder)) {
      marker.rollbackTo();

      if (!parseAnnotationMemberValuePairs(builder)) {
        annArgs.rollbackTo();
        return;
      }
    } else marker.drop();


    ParserUtils.getToken(builder, mNLS);

    if (!ParserUtils.getToken(builder, mRPAREN)) {
      builder.error(GroovyBundle.message("rparen.expected"));
    }
    annArgs.done(ANNOTATION_ARGUMENTS);
  }

  /*
  * annotationMemberValueInitializer ::=  conditionalExpression |	annotation
  */

  public static boolean parseAnnotationMemberValueInitializer(PsiBuilder builder) {
    if (builder.getTokenType() == mAT) {
      return Annotation.parse(builder);
    } else if(builder.getTokenType() == mLBRACK) {
      PsiBuilder.Marker marker = builder.mark();
      ParserUtils.getToken(builder, mLBRACK);
      while (Annotation.parse(builder));
      ParserUtils.getToken(builder, mRBRACK, GroovyBundle.message("rbrack.expected"));
      marker.done(ANNOTATION_ARRRAY_INITIALIZER);
      return true;
    }

    //check
    return ConditionalExpression.parse(builder) && !ParserUtils.getToken(builder, mASSIGN);
  }

  /*
   * anntotationMemberValuePairs ::= annotationMemberValuePair ( COMMA nls annotationMemberValuePair )*
   */

  private static boolean parseAnnotationMemberValuePairs(PsiBuilder builder) {
    PsiBuilder.Marker annmvps = builder.mark();

    if (!parseAnnotationMemberValueSinglePair(builder)) {
      annmvps.rollbackTo();
      return false;
    }

    while (ParserUtils.getToken(builder, mCOMMA)) {
      ParserUtils.getToken(builder, mNLS);

      if (!parseAnnotationMemberValueSinglePair(builder)) {
        annmvps.rollbackTo();
        return false;
      }
    }

    annmvps.done(ANNOTATION_MEMBER_VALUE_PAIRS);
    return true;
  }

  /*
   * annotationMemberValuePair ::= IDENT ASSIGN nls annotationMemberValueInitializer
   */

  private static boolean parseAnnotationMemberValueSinglePair(PsiBuilder builder) {
    PsiBuilder.Marker annmvp = builder.mark();

    if (!ParserUtils.getToken(builder, mIDENT)) {
      annmvp.rollbackTo();
      return false;
    }

    if (!ParserUtils.getToken(builder, mASSIGN)) {
      annmvp.rollbackTo();
      return false;
    }

    ParserUtils.getToken(builder, mNLS);

    if (parseAnnotationMemberValueInitializer(builder)) {
      annmvp.done(ANNOTATION_MEMBER_VALUE_PAIR);
      return true;
    }

    annmvp.rollbackTo();
    return true;
  }
}
