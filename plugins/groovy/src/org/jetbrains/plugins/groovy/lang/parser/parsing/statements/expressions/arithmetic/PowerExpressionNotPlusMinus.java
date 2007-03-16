package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arithmetic;

import org.jetbrains.plugins.groovy.lang.parser.parsing.Construction;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import com.intellij.lang.PsiBuilder;

/**
 * @author Ilya.Sergey
 */
public class PowerExpressionNotPlusMinus implements Construction {

  public static GroovyElementType parse(PsiBuilder builder){

    return  UnaryExpressionNotPlusMinus.parse(builder);

    // TODO realize me!
  }

}