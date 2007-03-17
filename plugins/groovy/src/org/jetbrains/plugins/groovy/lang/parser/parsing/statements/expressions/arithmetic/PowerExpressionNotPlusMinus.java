package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arithmetic;

import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import com.intellij.lang.PsiBuilder;

/**
 * @author Ilya.Sergey
 */
public class PowerExpressionNotPlusMinus implements GroovyElementTypes {

  public static GroovyElementType parse(PsiBuilder builder){

    return  UnaryExpressionNotPlusMinus.parse(builder);

    // TODO realize me!
  }

}