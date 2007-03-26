package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.constructor;

import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.blocks.OpenOrClosableBlock;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.Separators;
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
      cbMarker.rollbackTo();
      return WRONGWAY;
    }

    ParserUtils.getToken(builder, mNLS);

    if (!tWRONG_SET.contains(ExplicitConstructorStatement.parse(builder))) {

      //explicit constructor invocation
      if (!tWRONG_SET.contains(Separators.parse(builder))) {
        if (tWRONG_SET.contains(OpenOrClosableBlock.parseBlockBody(builder))) {
          cbMarker.rollbackTo();
          return WRONGWAY;
        }
      }

      cbMarker.done(CONSTRUCTOR_BODY);
      return CONSTRUCTOR_BODY;

    } else {
      //just list block statements
      if (tWRONG_SET.contains(OpenOrClosableBlock.parseBlockBody(builder))) {
        cbMarker.rollbackTo();
        return WRONGWAY;
      }
    }

    if (!ParserUtils.getToken(builder, mRCURLY)) {
      cbMarker.rollbackTo();
      return WRONGWAY;
    }

    cbMarker.done(METHOD_BODY);
    return METHOD_BODY;
  }
}
