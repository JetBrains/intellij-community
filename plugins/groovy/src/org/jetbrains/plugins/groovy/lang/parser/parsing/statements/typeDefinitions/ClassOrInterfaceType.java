package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions;

import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.PsiBuilder;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 20.03.2007
 */

public class ClassOrInterfaceType implements GroovyElementTypes {
  public static IElementType parse(PsiBuilder builder) {
    //todo: add cases

    PsiBuilder.Marker citMarker = builder.mark();

    if (!ParserUtils.getToken(builder, mIDENT)){
      citMarker.rollbackTo();
      return WRONGWAY;
    }

    citMarker.done(CLASS_INTERFACE_TYPE);    
    return CLASS_INTERFACE_TYPE;
  }
}
