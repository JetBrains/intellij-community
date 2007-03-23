package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.members;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.declaration.Declaration;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.declaration.DeclarationStart;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.ConstructorStart;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.TypeDefinitionInternal;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.blocks.OpenBlock;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.modifiers.Modifier;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.modifiers.ModifiersOptional;
import org.jetbrains.plugins.groovy.lang.parser.parsing.types.TypeDeclarationStart;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 20.03.2007
 */
public class ClassMember implements GroovyElementTypes {
  public static IElementType parse(PsiBuilder builder) {
    PsiBuilder.Marker cmMarker = builder.mark();
    //constructor
    PsiBuilder.Marker constructorStartMarker = builder.mark();
    if (ConstructorStart.parse(builder)) {
      constructorStartMarker.rollbackTo();

      if (tWRONG_SET.contains(Modifier.parse(builder))) {
        cmMarker.rollbackTo();
        return WRONGWAY;
      }

      if (tWRONG_SET.contains(ConstructorDefinition.parse(builder))) {
        cmMarker.rollbackTo();
        return WRONGWAY;
      }

      cmMarker.done(CONSTRUCTOR_DEFINITION);
    }
    constructorStartMarker.rollbackTo();

    //declaration
    PsiBuilder.Marker declMarker = builder.mark();
    if (DeclarationStart.parse(builder)) {
      declMarker.rollbackTo();
      return Declaration.parse(builder);
    }
    declMarker.rollbackTo();

    //type definition
    PsiBuilder.Marker typeDeclStartMarker = builder.mark();
    if (TypeDeclarationStart.parse(builder)) {
      typeDeclStartMarker.rollbackTo();

      if (tWRONG_SET.contains(ModifiersOptional.parse(builder))) {
        cmMarker.rollbackTo();
        return WRONGWAY;
      }

      IElementType typeDef = TypeDefinitionInternal.parse(builder);
      if (tWRONG_SET.contains(typeDef)) {
        cmMarker.rollbackTo();
        return WRONGWAY;
      }

      cmMarker.done(TYPE_DEFINITION_FIELD);
      return TYPE_DEFINITION_FIELD;
    }
    typeDeclStartMarker.rollbackTo();

    //static compound statement
    ParserUtils.getToken(builder, kSTATIC);

    GroovyElementType openBlock = OpenBlock.parse(builder);
    if (tWRONG_SET.contains(openBlock)) {
      cmMarker.rollbackTo();
      return WRONGWAY;
    }

    cmMarker.done(COMPOUND_STATEMENT);
    return COMPOUND_STATEMENT;
  }
}
