package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.typeDef;

import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.blocks.EnumBlock;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.ImplementsClause;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.PsiBuilder;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 16.03.2007
 */
public class EnumDefinition implements GroovyElementTypes {
  public static IElementType parse(PsiBuilder builder) {
//    PsiBuilder.Marker edMarker = builder.mark();

    if (!ParserUtils.getToken(builder, kENUM)){
//      edMarker.rollbackTo();
      return WRONGWAY;
    }

    if (!ParserUtils.getToken(builder, mIDENT)){
//      edMarker.rollbackTo();
      return WRONGWAY;
    }

    if (WRONGWAY.equals(ImplementsClause.parse(builder))) {
//      edMarker.rollbackTo();
      return WRONGWAY;
    }

    if (WRONGWAY.equals(EnumBlock.parse(builder))) {
//      edMarker.rollbackTo();
      return WRONGWAY;
    }

//    edMarker.done(ENUM_DEFINITION);
    return ENUM_DEFINITION;
  }
}
