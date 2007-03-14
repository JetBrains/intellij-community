package org.jetbrains.plugins.groovy.lang.parser.parsing.toplevel;

import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.Construction;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.Separators;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.Statement;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.GroovyBundle;
import com.intellij.lang.PsiBuilder;

/**
 * Main node of any Groovy script
 *
 * @autor: Dmitry.Krasilschikov, Ilya Sergey
 */
public class CompilationUnit implements Construction {

  public static GroovyElementType parse(PsiBuilder builder) {

    if (ParserUtils.tokenQuestion(builder, mSH_COMMENT)) {
      ParserUtils.tokenStrict(builder, mNLS, GroovyBundle.message("separator.expected"));
    }

    // TODO add package statement parsing

    Statement.parse(builder);
    GroovyElementType sepResult = Separators.parse(builder);
    while (!WRONGWAY.equals(sepResult)) {
      Statement.parse(builder);
      sepResult = Separators.parse(builder);
    }

    return GroovyElementTypes.COMPILATION_UNIT;
  }
}
