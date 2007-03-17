package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arithmetic;

import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import com.intellij.lang.PsiBuilder;

/**
 * @author Ilya.Sergey
 */
public class ShiftExpression implements GroovyElementTypes {

  public static GroovyElementType parse(PsiBuilder builder){

    return AdditiveExpression.parse(builder);

    // TODO realize me!

  }

}