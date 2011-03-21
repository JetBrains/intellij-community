/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
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
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.typeDef.AnnotationDefinition;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.typeDef.ClassDefinition;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.typeDef.EnumDefinition;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.typeDef.InterfaceDefinition;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.modifiers.Modifiers;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 16.03.2007
 */

/*
 * TypeDefinition ::= ClassDefinition
 *                          | InterfaceDefinition
 *                          | EnumDefinition
 *                          | AnnotationDefinition 
 */

public class TypeDefinition implements GroovyElementTypes {
  public static boolean parse(PsiBuilder builder, GroovyParser parser) {
    PsiBuilder.Marker tdMarker = builder.mark();
    Modifiers.parse(builder, parser);

    final IElementType tdType = parseAfterModifiers(builder, parser);
    if (tdType == WRONGWAY) {
      tdMarker.rollbackTo();
      return false;
    }

    tdMarker.done(tdType);
    return true;
  }

  public static IElementType parseAfterModifiers(PsiBuilder builder, GroovyParser parser) {
    if (builder.getTokenType() == kCLASS && ClassDefinition.parse(builder, parser)) {
      return CLASS_DEFINITION;
    }

    if (builder.getTokenType() == kINTERFACE && InterfaceDefinition.parse(builder, parser)) {
      return INTERFACE_DEFINITION;
    }

    if (builder.getTokenType() == kENUM && EnumDefinition.parse(builder, parser)) {
      return ENUM_DEFINITION;
    }

    if (builder.getTokenType() == mAT && AnnotationDefinition.parseAnnotationDefinition(builder, parser)) {
      return ANNOTATION_DEFINITION;
    }

    return WRONGWAY;
  }
}
