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

import org.jetbrains.plugins.groovy.lang.parser.parsing.types.TypeParameters;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.blocks.ClassBlock;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.SuperClassClause;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.ImplementsClause;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.GroovyBundle;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.PsiBuilder;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 16.03.2007
 */

/*
 * ClassDefinition ::= classdef IDENT nls [TypeParameters] superClassClause implementsClause classBlock
 */

public class ClassDefinition implements GroovyElementTypes {
  public static IElementType parse(PsiBuilder builder) {
    if (!ParserUtils.getToken(builder, kCLASS)) {
      return WRONGWAY;
    }

    if (!ParserUtils.getToken(builder, mIDENT)) {
      builder.error(GroovyBundle.message("identifier.expected"));
      return WRONGWAY;
    }

    ParserUtils.getToken(builder, mNLS);

    TypeParameters.parse(builder);

    if (kEXTENDS.equals(builder.getTokenType()))
      if (WRONGWAY.equals(SuperClassClause.parse(builder))) {
//      return WRONGWAY;
      }

    if (kIMPLEMENTS.equals(builder.getTokenType()))
      if (WRONGWAY.equals(ImplementsClause.parse(builder))) {
//      return WRONGWAY;
      }

    if (mLCURLY.equals(builder.getTokenType())) {
      if (WRONGWAY.equals(ClassBlock.parse(builder))) {
        return WRONGWAY;
      }
    } else {
      builder.error(GroovyBundle.message("lcurly.expected"));
    }

    return CLASS_DEFINITION;
  }
}
