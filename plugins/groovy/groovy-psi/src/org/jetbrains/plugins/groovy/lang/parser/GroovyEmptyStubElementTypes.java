// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.parser;

import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.stubs.elements.*;

public interface GroovyEmptyStubElementTypes {
  GrAnnotationArgumentListElementType ANNOTATION_ARGUMENT_LIST = GroovyElementTypes.ANNOTATION_ARGUMENT_LIST;
  GrEnumConstantListElementType ENUM_CONSTANTS = GroovyElementTypes.ENUM_CONSTANTS;
  GrTypeParameterListElementType TYPE_PARAMETER_LIST = GroovyElementTypes.TYPE_PARAMETER_LIST;
  GrParameterListElementType PARAMETER_LIST = GroovyElementTypes.PARAMETER_LIST;
  GrTypeDefinitionBodyElementType CLASS_BODY = GroovyElementTypes.CLASS_BODY;
  GrEnumDefinitionBodyElementType ENUM_BODY = GroovyElementTypes.ENUM_BODY;
}
