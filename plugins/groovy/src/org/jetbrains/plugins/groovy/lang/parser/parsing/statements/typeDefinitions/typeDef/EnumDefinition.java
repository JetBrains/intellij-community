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
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.ImplementsClause;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.blocks.EnumBlock;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.GroovyBundle;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 16.03.2007
 */
public class EnumDefinition implements GroovyElementTypes {
  public static GroovyElementType parse(PsiBuilder builder) {
    if (!ParserUtils.getToken(builder, kENUM)) {
      return WRONGWAY;
    }

    String name;
    if (!mIDENT.equals(builder.getTokenType())) {
      builder.error(GroovyBundle.message("identifier.expected"));
      return WRONGWAY;
    } else {
      name = builder.getTokenText();
      builder.advanceLexer();
    }

    if (WRONGWAY.equals(ImplementsClause.parse(builder))) {
      return ENUM_DEFINITION_ERROR;
    }

    if (WRONGWAY.equals(EnumBlock.parse(builder, name))) {
      return ENUM_DEFINITION_ERROR;
    }

    return ENUM_DEFINITION;
  }
}
