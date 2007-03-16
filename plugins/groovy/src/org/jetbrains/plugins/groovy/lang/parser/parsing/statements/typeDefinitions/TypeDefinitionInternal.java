package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions;

import org.jetbrains.plugins.groovy.lang.parser.parsing.Construction;
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
  
public class TypeDefinitionInternal implements Construction {
  public static IElementType parse(PsiBuilder builder) {
    if (!tWRONG_SET.contains(ClassDefinition.parse(builder))) return CLASS_DEFINITION;

    if (!tWRONG_SET.contains(InterfaceDefinition.parse(builder))) return INTERFACE_DEFINITION;

    if (!tWRONG_SET.contains(EnumDefinition.parse(builder))) return ENUM_DEFINITION;

    if (!tWRONG_SET.contains(AnnotationDefinition.parse(builder))) return ANNOTATION_DEFINITION;

    return WRONGWAY;
  }
}
