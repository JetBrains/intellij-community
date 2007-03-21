package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions;

import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.Separators;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.PsiBuilder;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 16.03.2007
 */
public class InterfaceBlock implements GroovyElementTypes {
  public static IElementType parse(PsiBuilder builder) {
    //see also InterfaceBlock, EnumBlock, AnnotationBlock
   PsiBuilder.Marker ibMarker = builder.mark();

    if (!ParserUtils.getToken(builder, mLCURLY)){
      ibMarker.rollbackTo();
      return WRONGWAY;
    }

    InterfaceMember.parse(builder);

    IElementType sep = Separators.parse(builder);

    while(!tWRONG_SET.contains(sep)){
      InterfaceMember.parse(builder);

      sep = Separators.parse(builder);
    }

    if (!ParserUtils.getToken(builder, mRCURLY)){
      ibMarker.rollbackTo();
      return WRONGWAY;
    }

    ibMarker.done(INTERFACE_BLOCK);
    return INTERFACE_BLOCK;
  }
}
