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
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.ImplementsClause;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.SuperClassClause;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.blocks.ClassBlock;
import org.jetbrains.plugins.groovy.lang.parser.parsing.types.TypeParameters;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 16.03.2007
 */

/*
 * ClassDefinition ::= classdef IDENT nls [TypeParameters] superClassClause implementsClause classBlock
 */

public class ClassDefinition implements GroovyElementTypes {
  public static GroovyElementType parse(PsiBuilder builder) {
    if (!ParserUtils.getToken(builder, kCLASS)) {
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

    ParserUtils.getToken(builder, mNLS);

    TypeParameters.parse(builder);

    if (kEXTENDS.equals(builder.getTokenType())) {
      SuperClassClause.parse(builder);
    }

    if (kIMPLEMENTS.equals(builder.getTokenType())) {
      ImplementsClause.parse(builder);
    }

    if (mLCURLY.equals(builder.getTokenType())) {
      if (WRONGWAY.equals(ClassBlock.parse(builder, name))) {
        return CLASS_DEFINITION_ERROR;
      }
    } else {
      builder.error(GroovyBundle.message("lcurly.expected"));
      return CLASS_DEFINITION_ERROR;
    }

    return CLASS_DEFINITION;
  }
}