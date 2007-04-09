package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.members;

import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.annotations.Annotation;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arguments.ArgumentList;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.blocks.ClassBlock;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.GroovyBundle;
import com.intellij.lang.PsiBuilder;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 06.04.2007
 */
public class EnumConstant implements GroovyElementTypes {
  public static GroovyElementType parse(PsiBuilder builder) {
    PsiBuilder.Marker ecMarker = builder.mark();
    ParserUtils.getToken(builder, mNLS);

    Annotation.parseAnnotationOptional(builder);

    if (!ParserUtils.getToken(builder, mIDENT)) {
      ecMarker.rollbackTo();
      return WRONGWAY;
    }

    if (ParserUtils.getToken(builder, mLPAREN)) {
      ArgumentList.parse(builder, mRPAREN);

      if (!ParserUtils.getToken(builder, mRPAREN)) {
        builder.error(GroovyBundle.message("rparen.expected"));
        ecMarker.rollbackTo();
        return WRONGWAY;
      }
    }

    if (ParserUtils.lookAhead(builder, mLCURLY)) {
      ClassBlock.parse(builder);
    }

    ecMarker.done(ENUM_CONSTANT);
    return ENUM_CONSTANT;

  }
}
