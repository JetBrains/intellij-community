package org.jetbrains.plugins.groovy.lang.parser.parsing.toplevel;

import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.toplevel.packaging.PackageDefinition;
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
public class CompilationUnit implements GroovyElementTypes {

  public static GroovyElementType parse(PsiBuilder builder) {

    ParserUtils.getToken(builder, mSH_COMMENT);
    ParserUtils.getToken(builder, mNLS);

    if (ParserUtils.lookAhead(builder, kPACKAGE)) {
      PackageDefinition.parse(builder);
    } else {
      Statement.parseWithImports(builder);
    }
    cleanAfterError(builder);

    GroovyElementType sepResult = Separators.parse(builder);
    while (!WRONGWAY.equals(sepResult)) {
      Statement.parseWithImports(builder);
      cleanAfterError(builder);
      sepResult = Separators.parse(builder);
    }

    return GroovyElementTypes.COMPILATION_UNIT;
  }

  /**
   * Marks some trash after statement parsing as error
   *
   * @param builder PsiBuilder
   */
  private static void cleanAfterError(PsiBuilder builder) {
    int i = 0;
    PsiBuilder.Marker em = builder.mark();
    while (!builder.eof() &&
            !(mNLS.equals(builder.getTokenType()) ||
                    mSEMI.equals(builder.getTokenType()))
            ) {
      builder.advanceLexer();
      i++;
    }
    if (i > 0) {
      em.error(GroovyBundle.message("separator.expected"));
    } else {
      em.drop();
    }
  }

}
