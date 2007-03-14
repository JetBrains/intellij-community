package org.jetbrains.plugins.groovy.lang.parser.parsing.toplevel.imports;

import org.jetbrains.plugins.groovy.lang.parser.parsing.Construction;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.GroovyBundle;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;

/**
 * Parses import statement
 *
 * @author Ilya Sergey
 */
public class ImportStatement implements Construction {

  public static GroovyElementType parse(PsiBuilder builder){

    Marker impMarker = builder.mark();

    ParserUtils.getToken(builder, kIMPORT, GroovyBundle.message("import.keyword.expected"));
    ParserUtils.getToken(builder, kSTATIC);
    IdentifierStar.parse(builder);

    impMarker.done(IMPORT_STATEMENT);

    return IMPORT_STATEMENT;
  }

}
