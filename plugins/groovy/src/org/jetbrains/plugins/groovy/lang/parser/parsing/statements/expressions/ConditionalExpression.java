package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions;

import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.logical.LogicalOrExpression;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import com.intellij.lang.PsiBuilder;

/**
 * @author Ilya.Sergey
 */
public class ConditionalExpression implements GroovyElementTypes {

  public static GroovyElementType parse(PsiBuilder builder){

    return LogicalOrExpression.parse(builder);

    // TODO realize me!


  }

}