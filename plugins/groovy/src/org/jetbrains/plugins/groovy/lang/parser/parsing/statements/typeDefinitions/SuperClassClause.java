package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions;

import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.GroovyBundle;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.PsiBuilder;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 16.03.2007
 */
public class SuperClassClause implements GroovyElementTypes {
  public static IElementType parse(PsiBuilder builder) {
    PsiBuilder.Marker sccMarker = builder.mark();

    if (!ParserUtils.getToken(builder, kEXTENDS)){
      sccMarker.rollbackTo();
      return NONE;
    }

    ParserUtils.getToken(builder, mNLS);

    if (tWRONG_SET.contains(ClassOrInterfaceType.parse(builder))){
      sccMarker.rollbackTo();
      builder.error(GroovyBundle.message("class.or.interface.type.expected"));
      return WRONGWAY;
    }

    ParserUtils.getToken(builder, mNLS);

    sccMarker.done(SUPER_CLASS_CLAUSE);
    return SUPER_CLASS_CLAUSE;
  }
}
