package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.blocks;

import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.auxilary.BalancedBrackets;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.Statement;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.Separators;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.GroovyBundle;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.PsiBuilder;

/**
 * @author Ilya.Sergey
 */
public class OpenOrClosableBlock implements GroovyElementTypes {

  public static GroovyElementType parse(PsiBuilder builder) {

    PsiBuilder.Marker marker = builder.mark();

    if (!ParserUtils.getToken(builder, mLCURLY)) {
      marker.drop();
      return WRONGWAY;
    }
    ParserUtils.getToken(builder, mNLS);
    closableBlockParamsOpt(builder);
    parseBlockBody(builder);
    ParserUtils.getToken(builder, mRCURLY, GroovyBundle.message("rcurly.expected"));
    marker.done(CLOSABLE_BLOCK);
    return CLOSABLE_BLOCK;
  }

  private static GroovyElementType closableBlockParamsOpt(PsiBuilder builder) {
    // TODO implement me!
    ParserUtils.getToken(builder, mNLS);
    return WRONGWAY;
  }

  private static GroovyElementType parseBlockBody(PsiBuilder builder) {
    if (mSEMI.equals(builder.getTokenType()) || mNLS.equals(builder.getTokenType())) {
      Separators.parse(builder);
    }

    GroovyElementType result = Statement.parse(builder);
    while (!result.equals(WRONGWAY) &&
            (mSEMI.equals(builder.getTokenType()) || mNLS.equals(builder.getTokenType()))) {
      Separators.parse(builder);
      result = Statement.parse(builder);
    }
    Separators.parse(builder);
    return BLOCK_BODY;
  }

}
