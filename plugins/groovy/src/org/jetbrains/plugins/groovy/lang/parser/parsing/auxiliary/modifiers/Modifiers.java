package org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.modifiers;

import org.jetbrains.plugins.groovy.lang.parser.parsing.Construction;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.annotations.Annotation;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.GroovyBundle;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.PsiBuilder;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 16.03.2007
 */

/*
 * Modifiers ::= "def" nls
 *              | {modifier nls}+
 *              | {annotation nls}+
 */

public class Modifiers implements Construction {
  public static IElementType parse(PsiBuilder builder) {
    PsiBuilder.Marker modifiersMarker = builder.mark();

    if (ParserUtils.lookAhead(builder, kDEF)) {
      ParserUtils.eatElement(builder, kDEF);

      ParserUtils.getToken(builder, mNLS);
      modifiersMarker.done(MODIFIER);
      return MODIFIER;
    }

    IElementType annotation = Annotation.parse(builder);
    IElementType modifier = Modifier.parse(builder);

    ParserUtils.getToken(builder, mNLS);

    if (!(ANNOTATION.equals(annotation) || MODIFIER.equals(modifier))) {
      modifiersMarker.rollbackTo();
      builder.error(GroovyBundle.message("annotation.or.modifier.expected"));
      return WRONGWAY;
    }

    while(ANNOTATION.equals(annotation) || MODIFIER.equals(modifier)) {
      annotation = Annotation.parse(builder);
      modifier = Modifier.parse(builder);

      ParserUtils.getToken(builder, mNLS);
    }

    modifiersMarker.done(MODIFIERS);

    return MODIFIERS;
  }
}