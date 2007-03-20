package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions;

import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arguments.CommandArguments;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import com.intellij.lang.PsiBuilder;

/**
 * Main classdef for any general expression parsing
 *
 * @author Ilya.Sergey
 */
public class ExpressionStatement implements GroovyElementTypes {

  public static GroovyElementType parse(PsiBuilder builder) {

    PsiBuilder.Marker marker = builder.mark();

    GroovyElementType result = AssignmentExpression.parse(builder);
    if (!WRONGWAY.equals(result) && !TokenSets.SEPARATORS.contains(builder.getTokenType())) {
      GroovyElementType res = CommandArguments.parse(builder);
      marker.done(EXPRESSION_STATEMENT);
      return EXPRESSION_STATEMENT;
    } else {
      marker.drop();
      return result;
    }
  }

  /**
   *
   * Use for parse expressions in Argument position 
   *
   * @param builder - Given builder
   * @return type of parsing result
   */
  public static GroovyElementType argParse(PsiBuilder builder) {
    GroovyElementType result = AssignmentExpression.parse(builder);
    if (!WRONGWAY.equals(result)) {
      return EXPRESSION_STATEMENT;
    }
      return result;
  }


  /**
   * Checks whether first token of current statement is valid
   *
   * @param builder given Builder
   * @return true begin symbols are valid
   */
  public static boolean suspiciousExpressionStatementStart(PsiBuilder builder) {
    return TokenSets.SUSPICIOUS_EXPRESSION_STATEMENT_START_TOKEN_SET.contains(builder.getTokenType());
  }

  /**
   * Continues expressioin first checking
   *
   * @param builder given builder
   * @return true if it is expression really
   */
  public static boolean checkSuspiciousExpressionStatement(PsiBuilder builder) {
    // TODO realize me!
    return true;
  }

}
