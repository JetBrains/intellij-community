package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions;

import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.typeDef.ClassDefinition;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.typeDef.InterfaceDefinition;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.typeDef.EnumDefinition;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.typeDef.AnnotationDefinition;
import org.jetbrains.plugins.groovy.GroovyBundle;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.PsiBuilder;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 16.03.2007
 */

/*
 * TypeDefinitionInternal ::= ClassDefinition
 *                          | InterfaceDefinition
 *                          | EnumDefinition
 *                          | AnnotationDefinition 
 */
  
public class TypeDefinitionInternal implements GroovyElementTypes {
  public static IElementType parse(PsiBuilder builder) {

    if (!WRONGWAY.equals(ClassDefinition.parse(builder))) return CLASS_DEFINITION;

    if (!WRONGWAY.equals(InterfaceDefinition.parse(builder))) return INTERFACE_DEFINITION;

    if (!WRONGWAY.equals(EnumDefinition.parse(builder))) return ENUM_DEFINITION;

    if (!WRONGWAY.equals(AnnotationDefinition.parse(builder))) return ANNOTATION_DEFINITION;

    builder.error(GroovyBundle.message("class.or.interface.or.enum.or.annotation.expected"));

    return WRONGWAY;
  }
}
