/*
 * Copyright 2000-2007 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.typeDef.AnnotationDefinition;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.typeDef.ClassDefinition;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.typeDef.EnumDefinition;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.typeDef.InterfaceDefinition;

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
  public static GroovyElementType parse(PsiBuilder builder) {

    if (builder.getTokenType() == kCLASS) return ClassDefinition.parse(builder);
    if (builder.getTokenType() == kINTERFACE) return InterfaceDefinition.parse(builder);
    if (builder.getTokenType() == kENUM) return EnumDefinition.parse(builder);
    if (builder.getTokenType() == mAT) return AnnotationDefinition.parse(builder);

    builder.error(GroovyBundle.message("class.or.interface.or.enum.or.annotation.expected"));
    return WRONGWAY;
  }
}
