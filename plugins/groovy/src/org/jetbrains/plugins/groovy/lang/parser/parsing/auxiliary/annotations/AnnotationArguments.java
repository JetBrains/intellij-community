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
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
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
  public static GroovyElementType parse(PsiBuilder builder) {

    PsiBuilder.Marker annArgs = builder.mark();
    if (parseAnnotationMemberValueInitializer(builder)) {
      annArgs.done(ANNOTATION_ARGUMENTS);
      return ANNOTATION_ARGUMENTS;
    }
    annArgs.rollbackTo();

    annArgs = builder.mark();
    if (!WRONGWAY.equals(parseAnnotationMemberValuePairs(builder))) {
      annArgs.done(ANNOTATION_ARGUMENTS);
      return ANNOTATION_ARGUMENTS;
    }

    annArgs.rollbackTo();
    return WRONGWAY;
  }

  /*
  * annotationMemberValueInitializer ::=  conditionalExpression |	annotation
  */

  public static boolean parseAnnotationMemberValueInitializer(PsiBuilder builder) {
    if (builder.getTokenType() == mAT) {
      return !WRONGWAY.equals(Annotation.parse(builder));
    }

    //check
    return !WRONGWAY.equals(ConditionalExpression.parse(builder)) && !ParserUtils.getToken(builder, mASSIGN);
  }

  /*
   * anntotationMemberValuePairs ::= annotationMemberValuePair ( COMMA nls annotationMemberValuePair )*
   */

  private static GroovyElementType parseAnnotationMemberValuePairs(PsiBuilder builder) {
    PsiBuilder.Marker annmvps = builder.mark();

    if (WRONGWAY.equals(parseAnnotationMemberValueSinglePair(builder))) {
      annmvps.rollbackTo();
      return WRONGWAY;
    }

    while (ParserUtils.getToken(builder, mCOMMA)) {
      ParserUtils.getToken(builder, mNLS);

      if (WRONGWAY.equals(parseAnnotationMemberValueSinglePair(builder))) {
        annmvps.rollbackTo();
        return WRONGWAY;
      }
    }

    annmvps.done(ANNOTATION_MEMBER_VALUE_PAIRS);
    return ANNOTATION_MEMBER_VALUE_PAIRS;
  }

  /*
   * annotationMemberValuePair ::= IDENT ASSIGN nls annotationMemberValueInitializer
   */

  private static GroovyElementType parseAnnotationMemberValueSinglePair(PsiBuilder builder) {
    PsiBuilder.Marker annmvp = builder.mark();

    if (!ParserUtils.getToken(builder, mIDENT)) {
      annmvp.rollbackTo();
      return WRONGWAY;
    }

    if (!ParserUtils.getToken(builder, mASSIGN)) {
      annmvp.rollbackTo();
      return WRONGWAY;
    }

    ParserUtils.getToken(builder, mNLS);

    if (parseAnnotationMemberValueInitializer(builder)) {
      annmvp.done(ANNOTATION_MEMBER_VALUE_PAIR);
      return ANNOTATION_MEMBER_VALUE_PAIR;
    }

    annmvp.rollbackTo();
    return ANNOTATION_MEMBER_VALUE_PAIR;
  }
}
