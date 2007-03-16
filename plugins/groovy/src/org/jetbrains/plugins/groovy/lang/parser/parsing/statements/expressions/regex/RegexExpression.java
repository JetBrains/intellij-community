package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.regex;

import org.jetbrains.plugins.groovy.lang.parser.parsing.Construction;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.relational.EqualityExpression;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import com.intellij.lang.PsiBuilder;

/**
 * @author Ilya.Sergey
 */
public class RegexExpression implements Construction {

  public static GroovyElementType parse(PsiBuilder builder){

    return EqualityExpression.parse(builder);

    // TODO realize me!

  }

}