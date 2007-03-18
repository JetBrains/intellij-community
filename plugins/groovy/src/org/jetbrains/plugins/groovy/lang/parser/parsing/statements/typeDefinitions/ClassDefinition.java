package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions;

import org.jetbrains.plugins.groovy.lang.parser.parsing.types.TypeParameters;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.PsiBuilder;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 16.03.2007
 */

/*
 * ClassDefinition ::= class IDENT nls [TypeParameters] superClassClause implementsClause classBlock
 */
  
public class ClassDefinition implements GroovyElementTypes {
  private static boolean showErrors = false;

  public static IElementType parse(PsiBuilder builder) {
    PsiBuilder.Marker classDefMarker = builder.mark();

    if (!ParserUtils.getToken(builder, kCLASS)) {
      classDefMarker.rollbackTo();
      return WRONGWAY;
    }

    if (!ParserUtils.getToken(builder, mIDENT)) {
      classDefMarker.rollbackTo();
      return WRONGWAY;
    }

    ParserUtils.getToken(builder, mNLS);

    TypeParameters.parse(builder);

    if (tWRONG_SET.contains(SuperClassClause.parse(builder))) {
      classDefMarker.rollbackTo();
      return WRONGWAY;
    }

    if (tWRONG_SET.contains(ImplementsClause.parse(builder))) {
      classDefMarker.rollbackTo();
      return WRONGWAY;
    }

    if (tWRONG_SET.contains(ClassBlock.parse(builder))) {
      classDefMarker.rollbackTo();
      return WRONGWAY;
    }

    classDefMarker.done(CLASS_DEFINITION);
    return CLASS_DEFINITION;
  }
}
