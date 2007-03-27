package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions;

import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.PsiBuilder;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 20.03.2007
 */

public class ClassOrInterfaceType implements GroovyElementTypes {
  public static GroovyElementType parse(PsiBuilder builder) {
    //todo: add cases

    PsiBuilder.Marker citMarker = builder.mark();

    if (!ParserUtils.getToken(builder, mIDENT)){
      citMarker.rollbackTo();
      return WRONGWAY;
    }

    citMarker.done(CLASS_INTERFACE_TYPE);    
    return CLASS_INTERFACE_TYPE;
  }

  /**
   * Strict parsing. In case of any convergence returns wrongway
   *
   * @param builder
   * @return
   */
  public static GroovyElementType parseStrict(PsiBuilder builder){
    PsiBuilder.Marker citMarker = builder.mark();
    if (!ParserUtils.getToken(builder, mIDENT)){
      citMarker.rollbackTo();
      return WRONGWAY;
    }

    citMarker.done(CLASS_INTERFACE_TYPE);
    return CLASS_INTERFACE_TYPE;
  }
}
