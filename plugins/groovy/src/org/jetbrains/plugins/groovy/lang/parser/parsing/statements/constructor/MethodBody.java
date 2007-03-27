package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.constructor;

import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.blocks.OpenOrClosableBlock;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.Separators;
import org.jetbrains.plugins.groovy.GroovyBundle;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.PsiBuilder;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 26.03.2007
 */
public class MethodBody implements GroovyElementTypes {
  public static IElementType parse(PsiBuilder builder) {
    PsiBuilder.Marker cbMarker = builder.mark();

    if (!ParserUtils.getToken(builder, mLCURLY)) {
      builder.error(GroovyBundle.message("lcurly.expected"));
      cbMarker.rollbackTo();
      return WRONGWAY;
    }

    ParserUtils.getToken(builder, mNLS);

    if (!WRONGWAY.equals(ExplicitConstructorStatement.parse(builder))) {

      builder.error(GroovyBundle.message("constructor.expected"));
      //explicit constructor invocation
      if (!WRONGWAY.equals(Separators.parse(builder))) {
        if (WRONGWAY.equals(OpenOrClosableBlock.parseBlockBody(builder))) {
          cbMarker.rollbackTo();
          return WRONGWAY;
        }
      }

      cbMarker.done(CONSTRUCTOR_BODY);
      return CONSTRUCTOR_BODY;

    } else {
      //just list block statements
      if (WRONGWAY.equals(OpenOrClosableBlock.parseBlockBody(builder))) {
        cbMarker.rollbackTo();
        return WRONGWAY;
      }
    }

    ParserUtils.waitNextRCurly(builder);

    if (!ParserUtils.getToken(builder, mRCURLY)) {
      builder.error(GroovyBundle.message("rcurly.expected"));
    }

    cbMarker.done(METHOD_BODY);
    return METHOD_BODY;
  }
}

