package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.members;

import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.annotations.Annotation;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arguments.ArgumentList;
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

    Annotation.parseAnnotationOptional(builder);

    if (!ParserUtils.getToken(builder, mIDENT)) {
      ecMarker.rollbackTo();
      return WRONGWAY;
    }

    if (!ParserUtils.getToken(builder, mLCURLY)) {
      EnumConstantBlock.parse(builder);
    } else {
      ArgumentList.parse(builder, mRCURLY);

      if (!ParserUtils.getToken(builder, mRCURLY)) {
        builder.error(GroovyBundle.message("rcurly.expected"));
        ecMarker.rollbackTo();
        return WRONGWAY;
      }
    }

    ecMarker.done(ENUM_CONSTANT);
    return ENUM_CONSTANT;

  }
}
