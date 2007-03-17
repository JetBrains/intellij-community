package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.relational;

import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arithmetic.ShiftExpression;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import com.intellij.lang.PsiBuilder;

/**
 * @author Ilya.Sergey
 */
public class RelationalExpression implements GroovyElementTypes {

  public static GroovyElementType parse(PsiBuilder builder){

    return ShiftExpression.parse(builder);

    // TODO realize me!

  }

}