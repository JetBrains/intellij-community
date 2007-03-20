package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.modifiers.ModifiersOptional;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.declaration.Declaration;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 20.03.2007
 */
public class InterfaceField implements GroovyElementTypes {
  public static IElementType parse(PsiBuilder builder) {
    IElementType interfaceFieldType = Declaration.parse(builder);

    if (!tWRONG_SET.contains(interfaceFieldType)) return interfaceFieldType;

    PsiBuilder.Marker ifMarker = builder.mark();
//    interfaceFieldType = TypeDeclarationStart.parse(builder);
//
//    if (tWRONG_SET.contains(interfaceFieldType)) {
//      ifMarker.rollbackTo();
//      return WRONGWAY;
//    }

    if (tWRONG_SET.contains(ModifiersOptional.parse(builder))) {
      ifMarker.rollbackTo();
      return WRONGWAY;
    }

    if (tWRONG_SET.contains(TypeDefinitionInternal.parse(builder))) {
      ifMarker.rollbackTo();
      return WRONGWAY;
    }

    ifMarker.done(INTERFACE_FIELD);
    return INTERFACE_FIELD;
  }
}
