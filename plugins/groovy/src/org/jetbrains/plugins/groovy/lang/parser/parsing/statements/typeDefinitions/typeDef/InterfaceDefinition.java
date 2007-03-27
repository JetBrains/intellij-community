package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.typeDef;

import org.jetbrains.plugins.groovy.lang.parser.parsing.types.TypeParameters;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.blocks.InterfaceBlock;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.InterfaceExtends;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.PsiBuilder;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 16.03.2007
 */
public class InterfaceDefinition implements GroovyElementTypes {
  public static IElementType parse(PsiBuilder builder) {
    if (!ParserUtils.getToken(builder, kINTERFACE)) {
      return WRONGWAY;
    }

    if (!ParserUtils.getToken(builder, mIDENT)) {
      return WRONGWAY;
    }

    ParserUtils.getToken(builder, mNLS);

    TypeParameters.parse(builder);

    if (WRONGWAY.equals(InterfaceExtends.parse(builder))) {
      return WRONGWAY;
    }

    if (WRONGWAY.equals(InterfaceBlock.parse(builder))) {
      return WRONGWAY;
    }

    return INTERFACE_DEFINITION;
  }
}
