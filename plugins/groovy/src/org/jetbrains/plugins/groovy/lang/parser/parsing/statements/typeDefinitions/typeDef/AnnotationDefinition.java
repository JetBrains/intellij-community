package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.typeDef;

import com.intellij.psi.tree.IElementType;
import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.blocks.AnnotationBlock;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 16.03.2007
 */
public class AnnotationDefinition implements GroovyElementTypes {
  public static IElementType parse(PsiBuilder builder) {
//    PsiBuilder.Marker adMarker = builder.mark();

    if (!ParserUtils.getToken(builder, mAT)) {
//      adMarker.rollbackTo();
      return WRONGWAY;
    }

    if (!ParserUtils.getToken(builder, kINTERFACE)) {
//      adMarker.rollbackTo();
      return WRONGWAY;
    }

    if (!ParserUtils.getToken(builder, mIDENT)) {
//      adMarker.rollbackTo();
      return WRONGWAY;
    }

    if (tWRONG_SET.contains(AnnotationBlock.parse(builder))) {
//      adMarker.rollbackTo();
      return WRONGWAY;
    }

//    adMarker.done(ANNOTATION_BLOCK);
    return ANNOTATION_BLOCK;
  }
}
