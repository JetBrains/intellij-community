package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.declaration;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.modifiers.Modifiers;
import org.jetbrains.plugins.groovy.lang.parser.parsing.types.TypeSpec;
import org.jetbrains.plugins.groovy.GroovyBundle;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 14.03.2007
 */

/*
 * Declaration ::= modifiers [TypeSpec] VariableDefinitions
 *                  | TypeSpec VariableDefinitions
 */

public class Declaration implements GroovyElementTypes {
  public static GroovyElementType parse(PsiBuilder builder) {
    //allows error messages
    IElementType modifiers = Modifiers.parse(builder);

    if (!tWRONG_SET.contains(modifiers)) {
      TypeSpec.parse(builder);

      if (tWRONG_SET.contains(VariableDefinitions.parse(builder))) {
        builder.error(GroovyBundle.message("variable.definitions.expected"));
        return WRONGWAY;
      }
    } else {
      if (tWRONG_SET.contains(TypeSpec.parse(builder))) {
        builder.error(GroovyBundle.message("type.specification.expected"));
        return WRONGWAY;
      }

      if (tWRONG_SET.contains(VariableDefinitions.parse(builder))) {
        builder.error(GroovyBundle.message("variable.definitions.expected"));
        return WRONGWAY;
      }
    }

    return DECLARATION;
  }
}
