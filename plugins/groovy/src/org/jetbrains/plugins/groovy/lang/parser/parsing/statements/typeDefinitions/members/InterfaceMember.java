package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.members;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.modifiers.ModifiersOptional;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.TypeDefinitionInternal;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.declaration.DeclarationStart;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.declaration.Declaration;
import org.jetbrains.plugins.groovy.lang.parser.parsing.types.TypeDeclarationStart;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 20.03.2007
 */
public class InterfaceMember implements GroovyElementTypes {
  public static IElementType parse(PsiBuilder builder) {

    //type definition field
    PsiBuilder.Marker ifMarker = builder.mark();

    PsiBuilder.Marker typeDeclStartMarker = builder.mark();
    if (TypeDeclarationStart.parse(builder)) {
      typeDeclStartMarker.rollbackTo();

      if (WRONGWAY.equals(ModifiersOptional.parse(builder))) {
        ifMarker.rollbackTo();
        return WRONGWAY;
      }

      IElementType typeDef = TypeDefinitionInternal.parse(builder);
      if (WRONGWAY.equals(typeDef)) {
        ifMarker.rollbackTo();
        return WRONGWAY;
      }

      ifMarker.done(typeDef);
      return typeDef;
    }
    typeDeclStartMarker.rollbackTo();
    ifMarker.rollbackTo();

    //declaration
    PsiBuilder.Marker declStartMarker = builder.mark();
    if (DeclarationStart.parse(builder)) {
      declStartMarker.rollbackTo();
      return Declaration.parse(builder);
    }
    declStartMarker.rollbackTo();

    return WRONGWAY;
  }
}
