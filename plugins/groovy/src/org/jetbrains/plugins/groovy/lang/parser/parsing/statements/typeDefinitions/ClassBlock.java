package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions;

import com.intellij.psi.tree.IElementType;
import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.Separators;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 16.03.2007
 */
public class ClassBlock implements GroovyElementTypes {
  public static IElementType parse(PsiBuilder builder) {
    //see also InterfaceBlock, EnumBlock, AnnotationBlock
    PsiBuilder.Marker cbMarker = builder.mark();

    if (!ParserUtils.getToken(builder, mLCURLY)){
      cbMarker.rollbackTo();
      return WRONGWAY;
    }

    ClassMember.parse(builder);

    while(ParserUtils.lookAhead(builder, Separators.parse(builder))){
      ParserUtils.getToken(builder, Separators.parse(builder));

      ClassMember.parse(builder);
    }

    if (!ParserUtils.getToken(builder, mRCURLY)){
      cbMarker.rollbackTo();
      return WRONGWAY;
    }

    cbMarker.done(CLASS_BLOCK);
    return CLASS_BLOCK;
  }
}
