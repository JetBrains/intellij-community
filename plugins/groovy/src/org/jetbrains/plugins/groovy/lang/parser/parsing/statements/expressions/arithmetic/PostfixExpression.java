package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arithmetic;

import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import com.intellij.lang.PsiBuilder;

/**
 * @author Ilya.Sergey
 */
public class PostfixExpression implements GroovyElementTypes {

  public static GroovyElementType parse(PsiBuilder builder){

    return PathExpression.parse(builder);

    // TODO realize me!

  }

}
