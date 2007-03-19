package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions;

import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.logical.LogicalOrExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.GroovyBundle;
import com.intellij.lang.PsiBuilder;

/**
 * @author Ilya.Sergey
 */
public class ConditionalExpression implements GroovyElementTypes {

  public static GroovyElementType parse(PsiBuilder builder){

    PsiBuilder.Marker marker = builder.mark();
    GroovyElementType result = LogicalOrExpression.parse(builder);
    if (ParserUtils.getToken(builder, mQUESTION)) {
      ParserUtils.getToken(builder, mNLS);
      AssignmentExpression.parse(builder);
      if (ParserUtils.getToken(builder, mCOLON, GroovyBundle.message("colon.expected"))){
        ParserUtils.getToken(builder, mNLS);
        parse(builder);
      }
      marker.done(CONDITIONAL_EXPRESSION);
    } else {
      marker.drop();
    }
    return result;
    

  }

}