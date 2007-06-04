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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.typeDef;

import com.intellij.lang.PsiBuilder;
import com.intellij.openapi.graph.builder.GraphBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.blocks.AnnotationBlock;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 16.03.2007
 */
public class AnnotationDefinition implements GroovyElementTypes {
  public static GroovyElementType parse(PsiBuilder builder) {
    if (!ParserUtils.getToken(builder, mAT)) {
      return WRONGWAY;
    }

    if (!ParserUtils.getToken(builder, kINTERFACE)) {
      return WRONGWAY;
    }

    if (!ParserUtils.getToken(builder, mIDENT)) {
      builder.error(GraphBundle.message("annotation.definition.qualified.name.expected"));
      return WRONGWAY;
    }

    if (WRONGWAY.equals(AnnotationBlock.parse(builder))) {
      return ANNOTATION_BLOCK;
    }

    return ANNOTATION_DEFINITION;
  }
}
