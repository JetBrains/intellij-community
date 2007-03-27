package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.blocks;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.Separators;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.members.ClassMember;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.GroovyBundle;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 16.03.2007
 */
public class ClassBlock implements GroovyElementTypes {
  public static IElementType parse(PsiBuilder builder) {
    //see also InterfaceBlock, EnumBlock, AnnotationBlock
    PsiBuilder.Marker cbMarker = builder.mark();

    if (!ParserUtils.getToken(builder, mLCURLY)) {
      builder.error(GroovyBundle.message("lcurly.expected"));
      cbMarker.rollbackTo();
      return WRONGWAY;
    }

    ClassMember.parse(builder);

    IElementType sep = Separators.parse(builder);

    while (!WRONGWAY.equals(sep)) {
      ClassMember.parse(builder);

      sep = Separators.parse(builder);
    }

    ParserUtils.waitNextRCurly(builder);

    if (!ParserUtils.getToken(builder, mRCURLY)) {
      builder.error(GroovyBundle.message("rcurly.expected"));
//      cbMarker.rollbackTo();
//      return WRONGWAY;
    }

    cbMarker.done(CLASS_BLOCK);
    return CLASS_BLOCK;
  }
}
