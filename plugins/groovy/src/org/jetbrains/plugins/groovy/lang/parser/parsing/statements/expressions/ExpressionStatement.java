package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions;

import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arguments.CommandArguments;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import com.intellij.lang.PsiBuilder;

/**
 * Main class for any general expression parsing
 *
 * @author Ilya.Sergey
 */
public class ExpressionStatement implements GroovyElementTypes {

  public static GroovyElementType parse(PsiBuilder builder) {
    GroovyElementType result = AssignmentExpression.parse(builder);
    if (!WRONGWAY.equals(result)) {
      CommandArguments.parse(builder);
      return EXPRESSION_STATEMENT;
    }
    return WRONGWAY;
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
