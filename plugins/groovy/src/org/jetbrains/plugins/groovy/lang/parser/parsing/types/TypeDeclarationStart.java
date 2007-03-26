package org.jetbrains.plugins.groovy.lang.parser.parsing.types;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.modifiers.ModifiersOptional;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 20.03.2007
 */
public class TypeDeclarationStart implements GroovyElementTypes {
  public static boolean  parse(PsiBuilder builder) {
    if (tWRONG_SET.contains(ModifiersOptional.parse(builder))) {
      return false;
    }

    if (!ParserUtils.getToken(builder, kCLASS))
      if (!ParserUtils.getToken(builder, kINTERFACE))
        if (!ParserUtils.getToken(builder, kENUM))
          if (!(ParserUtils.getToken(builder, mAT) && ParserUtils.getToken(builder, kINTERFACE))) {
//            builder.error(GroovyBundle.message("class.interface.enum.or.at.interface.expected"));
            return false;
          }

    return true;
  }
}
