package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arithmetic;

import org.jetbrains.plugins.groovy.lang.parser.parsing.Construction;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.primary.PrimaryExpression;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import com.intellij.lang.PsiBuilder;

/**
 * @author Ilya.Sergey
 */
public class PathExpression implements Construction {

  public static GroovyElementType parse(PsiBuilder builder){

    return PrimaryExpression.parse(builder);

    // TODO realize me!

  }

}
