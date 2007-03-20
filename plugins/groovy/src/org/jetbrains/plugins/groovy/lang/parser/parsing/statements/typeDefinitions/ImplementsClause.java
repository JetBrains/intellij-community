package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 16.03.2007
 */
public class ImplementsClause implements GroovyElementTypes {
  public static IElementType parse(PsiBuilder builder) {
    //see also InterfaceExtends

    PsiBuilder.Marker isMarker = builder.mark();

    if (!ParserUtils.getToken(builder, kIMPLEMENTS)) {
      isMarker.rollbackTo();
      return NONE;
    }

    ParserUtils.getToken(builder, mNLS);

    if (tWRONG_SET.contains(ClassOrInterfaceType.parse(builder))) {
      isMarker.rollbackTo();
      return WRONGWAY;
    }

    while (ParserUtils.lookAhead(builder, mCOMMA)) {
      ParserUtils.getToken(builder, mNLS);

      ParserUtils.getToken(builder, mCOMMA);

      if (tWRONG_SET.contains(ClassOrInterfaceType.parse(builder))) {
        isMarker.rollbackTo();
        return WRONGWAY;
      }
    }

    isMarker.done(IMPLEMENTS_CLAUSE);
    return IMPLEMENTS_CLAUSE;
  }
}
