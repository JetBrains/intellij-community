package org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.annotations;

import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.ConditionalExpression;
import com.intellij.lang.PsiBuilder;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 03.04.2007
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

  private static boolean parseAnnotationMemberValueInitializer(PsiBuilder builder) {
    if (ParserUtils.lookAhead(builder, mAT)) {
      return !WRONGWAY.equals(Annotation.parse(builder));
    }

    //check
    return !WRONGWAY.equals(ConditionalExpression.parse(builder));
  }

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
