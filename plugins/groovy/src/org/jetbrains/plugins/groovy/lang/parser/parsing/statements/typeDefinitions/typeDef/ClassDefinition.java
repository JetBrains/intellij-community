package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.typeDef;

import org.jetbrains.plugins.groovy.lang.parser.parsing.types.TypeParameters;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.blocks.ClassBlock;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.SuperClassClause;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.ImplementsClause;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.GroovyBundle;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.PsiBuilder;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 16.03.2007
 */

/*
 * ClassDefinition ::= classdef IDENT nls [TypeParameters] superClassClause implementsClause classBlock
 */

public class ClassDefinition implements GroovyElementTypes {
  public static IElementType parse(PsiBuilder builder) {
    if (!ParserUtils.getToken(builder, kCLASS)) {
      return WRONGWAY;
    }

    if (!ParserUtils.getToken(builder, mIDENT)) {
      builder.error(GroovyBundle.message("identifier.expected"));
      return WRONGWAY;
    }

    ParserUtils.getToken(builder, mNLS);

    TypeParameters.parse(builder);

    if (kEXTENDS.equals(builder.getTokenType()))
      if (tWRONG_SET.contains(SuperClassClause.parse(builder))) {
//      return WRONGWAY;
      }

    if (kIMPLEMENTS.equals(builder.getTokenType()))
      if (tWRONG_SET.contains(ImplementsClause.parse(builder))) {
//      return WRONGWAY;
      }

    if (mLCURLY.equals(builder.getTokenType())) {
      if (tWRONG_SET.contains(ClassBlock.parse(builder))) {
        return WRONGWAY;
      }
    } else {
      builder.error(GroovyBundle.message("lcurly.expected"));
    }

    return CLASS_DEFINITION;
  }
}
