package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.declaration;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.groovy.lang.parser.parsing.Construction;
import org.jetbrains.plugins.groovy.lang.parser.parsing.types.BuiltlnType;
import org.jetbrains.plugins.groovy.lang.parser.parsing.types.QualifiedTypeName;
import org.jetbrains.plugins.groovy.lang.parser.parsing.identifier.UpperCaseIdent;
import org.jetbrains.plugins.groovy.lang.parser.parsing.identifier.Ident;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.Modifier;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 14.03.2007
 */
public class DeclarationStart implements Construction {
  public static IElementType parse(PsiBuilder builder) {
    IElementType elementType;

    if (ParserUtils.getToken(builder, kDEF)) return kDEF;

    elementType = Modifier.parse(builder);
    if (!WRONG_SET.contains(elementType)) return elementType;

    PsiBuilder.Marker declStartMarker = builder.mark();
    if (ParserUtils.getToken(builder, mAT)) {
      elementType = Ident.parse(builder);

      if (!WRONG_SET.contains(elementType)) {
        declStartMarker.done(DECLARATION_START);
        return DECLARATION_START;
      } else {
        declStartMarker.drop();
      }
    }

    elementType = UpperCaseIdent.parse(builder);
    if (!WRONG_SET.contains(elementType)) {
      
    }

    return WRONGWAY;


  }
}
