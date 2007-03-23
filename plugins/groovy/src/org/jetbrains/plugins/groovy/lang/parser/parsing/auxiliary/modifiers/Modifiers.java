package org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.modifiers;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.annotations.Annotation;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 16.03.2007
 */

/*
 * Modifiers ::= "def" nls
 *              | {modifier nls}+
 *              | {annotation nls}+
 */

public class Modifiers implements GroovyElementTypes {
  public static IElementType parse(PsiBuilder builder) {
    PsiBuilder.Marker modifiersMarker = builder.mark();

    if (ParserUtils.lookAhead(builder, kDEF)) {
      ParserUtils.eatElement(builder, MODIFIER);

      ParserUtils.getToken(builder, mNLS);
      modifiersMarker.drop();
      return MODIFIER;
    }

    IElementType annotation = Annotation.parse(builder);
    IElementType modifier = Modifier.parse(builder);

    ParserUtils.getToken(builder, mNLS);

    if (!(ANNOTATION.equals(annotation) || MODIFIER.equals(modifier))) {
      modifiersMarker.rollbackTo();
//      builder.error(GroovyBundle.message("annotation.or.modifier.expected"));
      return WRONGWAY;
    }

    boolean moreThanOneModifier = false;
    while(ANNOTATION.equals(annotation) || MODIFIER.equals(modifier)) {
      annotation = Annotation.parse(builder);
      modifier = Modifier.parse(builder);

      ParserUtils.getToken(builder, mNLS);

      moreThanOneModifier = true;
    }

    if (moreThanOneModifier) {
      modifiersMarker.done(MODIFIERS);
      return MODIFIERS;
    } else {
      modifiersMarker.done(MODIFIER);
      return MODIFIER;
    }
  }
}