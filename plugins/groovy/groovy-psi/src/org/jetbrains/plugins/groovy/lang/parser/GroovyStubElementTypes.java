// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.parser;

import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.stubs.elements.*;

public interface GroovyStubElementTypes {
  GrClassDefinitionElementType CLASS_TYPE_DEFINITION = GroovyElementTypes.CLASS_TYPE_DEFINITION;
  GrInterfaceDefinitionElementType INTERFACE_TYPE_DEFINITION = GroovyElementTypes.INTERFACE_TYPE_DEFINITION;
  GrEnumDefinitionElementType ENUM_TYPE_DEFINITION = GroovyElementTypes.ENUM_TYPE_DEFINITION;
  GrAnnotationDefinitionElementType ANNOTATION_TYPE_DEFINITION = GroovyElementTypes.ANNOTATION_TYPE_DEFINITION;
  GrAnonymousElementType ANONYMOUS_TYPE_DEFINITION = GroovyElementTypes.ANONYMOUS_TYPE_DEFINITION;
  GrTraitElementType TRAIT_TYPE_DEFINITION = GroovyElementTypes.TRAIT_TYPE_DEFINITION;
  GrEnumConstantInitializerElementType ENUM_CONSTANT_INITIALIZER = GroovyElementTypes.ENUM_CONSTANT_INITIALIZER;

  GrEnumConstantElementType ENUM_CONSTANT = GroovyElementTypes.ENUM_CONSTANT;
  GrFieldElementType FIELD = GroovyElementTypes.FIELD;
  GrMethodElementType METHOD = GroovyElementTypes.METHOD;
  GrAnnotationMethodElementType ANNOTATION_METHOD = GroovyElementTypes.ANNOTATION_METHOD;

  GrImplementsClauseElementType IMPLEMENTS_CLAUSE = GroovyElementTypes.IMPLEMENTS_CLAUSE;
  GrExtendsClauseElementType EXTENDS_CLAUSE = GroovyElementTypes.EXTENDS_CLAUSE;

  GrPackageDefinitionElementType PACKAGE_DEFINITION = GroovyElementTypes.PACKAGE_DEFINITION;

  GrImportStatementElementType IMPORT = GroovyElementTypes.IMPORT;

  GrTypeParameterElementType TYPE_PARAMETER = GroovyElementTypes.TYPE_PARAMETER;

  GrTypeParameterBoundsElementType TYPE_PARAMETER_BOUNDS_LIST = GroovyElementTypes.TYPE_PARAMETER_BOUNDS_LIST;

  GrMethodElementType CONSTRUCTOR = GroovyElementTypes.CONSTRUCTOR;

  GrThrowsClauseElementType THROWS_CLAUSE = GroovyElementTypes.THROWS_CLAUSE;

  GrNameValuePairElementType ANNOTATION_MEMBER_VALUE_PAIR = GroovyElementTypes.ANNOTATION_MEMBER_VALUE_PAIR;

  GrAnnotationElementType ANNOTATION = GroovyElementTypes.ANNOTATION;

  GrParameterElementType PARAMETER = GroovyElementTypes.PARAMETER;

  GrVariableDeclarationElementType VARIABLE_DECLARATION = GroovyElementTypes.VARIABLE_DECLARATION;
  GrVariableElementType VARIABLE = GroovyElementTypes.VARIABLE;

  GrModifierListElementType MODIFIER_LIST = GroovyElementTypes.MODIFIER_LIST;
}
