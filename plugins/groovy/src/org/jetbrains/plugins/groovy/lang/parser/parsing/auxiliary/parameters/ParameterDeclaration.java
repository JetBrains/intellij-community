package org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.parameters;

import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.modifiers.ParameterModifierOptional;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.VariableInitializer;
import org.jetbrains.plugins.groovy.lang.parser.parsing.types.TypeSpec;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.PsiBuilder;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 26.03.2007
 */
public class ParameterDeclaration implements GroovyElementTypes {
  public static IElementType parse(PsiBuilder builder) {
    if (!ParserUtils.lookAhead(builder, kFINAL) && !ParserUtils.lookAhead(builder, kDEF) && !ParserUtils.lookAhead(builder, mAT)) {
      return WRONGWAY;
    }

    PsiBuilder.Marker pdMarker = builder.mark();

    if (tWRONG_SET.contains(ParameterModifierOptional.parse(builder))){
      pdMarker.rollbackTo();
      return WRONGWAY;
    }

    TypeSpec.parse(builder);

    ParserUtils.getToken(builder, mTRIPLE_DOT);

    if (!ParserUtils.getToken(builder, mIDENT)){
      pdMarker.rollbackTo();
      return WRONGWAY;
    }

    VariableInitializer.parse(builder);

    pdMarker.done(PARAMETER);
    return PARAMETER;
  }
}
