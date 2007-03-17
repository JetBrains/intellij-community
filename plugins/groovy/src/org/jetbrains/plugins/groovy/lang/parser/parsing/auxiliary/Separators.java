package org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary;

import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import com.intellij.lang.PsiBuilder;

/**
 * Parse separators
 *
 * @author Ilya Sergey
 */
public class Separators implements GroovyElementTypes {

  public static GroovyElementType parse(PsiBuilder builder){
    if (mSEMI.equals(builder.getTokenType())){
      builder.advanceLexer();
      while (ParserUtils.getToken(builder, mNLS)) {
        // Parse newLines
      }
      return SEP;
    } else if (mNLS.equals(builder.getTokenType())) {
      builder.advanceLexer();
      while (ParserUtils.getToken(builder, mSEMI)) {
        while (ParserUtils.getToken(builder, mNLS)){
          // Parse newLines
        }
      }
      return SEP;
    }
    return WRONGWAY;
  }

}
