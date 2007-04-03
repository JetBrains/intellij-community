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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.members;

import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.parameters.ParameterDeclarationList;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.ThrowClause;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.NlsWarn;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.constructor.ConstructorBody;
import org.jetbrains.plugins.groovy.GroovyBundle;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.PsiBuilder;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 23.03.2007
 */
public class ConstructorDefinition implements GroovyElementTypes {
  public static IElementType parse(PsiBuilder builder) {
    if (!ParserUtils.getToken(builder, mIDENT)) {
      builder.error(GroovyBundle.message("identifier.expected"));
      return WRONGWAY;
    }

    if (!ParserUtils.getToken(builder, mLPAREN)) {
      builder.error(GroovyBundle.message("lparen.expected"));
    }

    ParameterDeclarationList.parse(builder, mRPAREN);

    if (!ParserUtils.getToken(builder, mRPAREN)) {
      ParserUtils.waitNextRCurly(builder);

      builder.error(GroovyBundle.message("rparen.expected"));
    }

    ThrowClause.parse(builder);

    NlsWarn.parse(builder);

    IElementType methodBody = ConstructorBody.parse(builder);

   /* if (METHOD_BODY.equals(methodBody)) {
      return METHOD_DEFINITION;
    } else */
    if (CONSTRUCTOR_BODY.equals(methodBody)) {
      return CONSTRUCTOR_DEFINITION;
    } else {
      return WRONGWAY;
    }
  }
}