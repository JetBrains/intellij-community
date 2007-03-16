package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions;

import org.jetbrains.plugins.groovy.lang.parser.parsing.Construction;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import com.intellij.lang.PsiBuilder.*;
import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.TokenSet;


/**
 * @author Ilya.Sergey
 */
public class AssignmentExpression implements Construction {

  private static final TokenSet ASSIGNMENTS = TokenSet.create(
          mASSIGN,
          mPLUS_ASSIGN,
          mMINUS_ASSIGN,
          mSTAR_ASSIGN,
          mDIV_ASSIGN,
          mMOD_ASSIGN,
          mSR_ASSIGN,
          mBSR_ASSIGN,
          mSL_ASSIGN,
          mBAND_ASSIGN,
          mBOR_ASSIGN,
          mBXOR_ASSIGN,
          mSTAR_STAR_ASSIGN
  );

  public static GroovyElementType parse(PsiBuilder builder){
    Marker marker = builder.mark();
    GroovyElementType result = ConditionalExpression.parse(builder);
    if (ParserUtils.getToken(builder, ASSIGNMENTS)) {
      ParserUtils.getToken(builder, mNLS);
      parse(builder);
      marker.done(ASSIGNMENT_EXXPRESSION);
      return ASSIGNMENT_EXXPRESSION;
    } else {
      marker.drop();
    }

    return result;
  }


}
