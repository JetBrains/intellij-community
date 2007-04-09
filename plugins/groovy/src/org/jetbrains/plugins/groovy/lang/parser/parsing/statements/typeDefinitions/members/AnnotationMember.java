package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.members;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.TypeDefinition;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.declaration.VariableDefinitions;
import org.jetbrains.plugins.groovy.lang.parser.parsing.types.TypeDeclarationStart;
import org.jetbrains.plugins.groovy.lang.parser.parsing.types.TypeSpec;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 03.04.2007
 */
public class AnnotationMember implements GroovyElementTypes {
  public static GroovyElementType parse(PsiBuilder builder) {

    //type definition
    PsiBuilder.Marker typeDeclStartMarker = builder.mark();
    if (TypeDeclarationStart.parse(builder)) {
      typeDeclStartMarker.rollbackTo();

      GroovyElementType typeDef = TypeDefinition.parse(builder);
      if (WRONGWAY.equals(typeDef)) {
        return WRONGWAY;
      }
      return typeDef;
    }
    typeDeclStartMarker.rollbackTo();


    PsiBuilder.Marker varDefMarker = builder.mark();

    //typized var definition
    //todo: check for upper case type specification 
    if (WRONGWAY.equals(TypeSpec.parse(builder))) {
      varDefMarker.rollbackTo();
      return WRONGWAY;
    }

    GroovyElementType varDef = VariableDefinitions.parse(builder);
    if (!WRONGWAY.equals(varDef)) {
      varDefMarker.done(varDef);
      return varDef;
    }
    varDefMarker.rollbackTo();

    return WRONGWAY;
  }
}
