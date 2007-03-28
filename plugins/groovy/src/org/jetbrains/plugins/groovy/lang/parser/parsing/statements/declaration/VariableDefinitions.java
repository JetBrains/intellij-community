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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.declaration;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.NlsWarn;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.ThrowClause;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.parameters.ParameterDeclarationList;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.AssignmentExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.blocks.OpenOrClosableBlock;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 16.03.2007
 */
public class VariableDefinitions implements GroovyElementTypes {
  public static GroovyElementType parse(PsiBuilder builder) {
//    PsiBuilder.Marker vdMarker = builder.mark();

    if (!(ParserUtils.lookAhead(builder, mIDENT) || ParserUtils.lookAhead(builder, mSTRING_LITERAL))) {
      builder.error(GroovyBundle.message("indentifier.or.string.literal.expected"));
//      vdMarker.rollbackTo();
      return WRONGWAY;
    }

    PsiBuilder.Marker varMarker = builder.mark();
    if ((ParserUtils.getToken(builder, mIDENT) || ParserUtils.getToken(builder, mSTRING_LITERAL)) && ParserUtils.getToken(builder, mLPAREN)) {

      ParameterDeclarationList.parse(builder);
      if (!ParserUtils.getToken(builder, mRPAREN)) {
        ParserUtils.waitNextRCurly(builder);

        builder.error(GroovyBundle.message("rparen.expected"));
      }

      ThrowClause.parse(builder);

      NlsWarn.parse(builder);

      OpenOrClosableBlock.parseOpenBlock(builder);

      varMarker.drop();
//      vdMarker.done(METHOD_DEFINITION);
      return METHOD_DEFINITION;
    } else {
      varMarker.rollbackTo();
    }

    if (parseVariableDeclarator(builder)) {
      while (ParserUtils.getToken(builder, mCOMMA)) {
        ParserUtils.getToken(builder, mNLS);

        parseVariableDeclarator(builder);
      }

//      vdMarker.done(VARIABLE_DEFINITION);
      return VARIABLE_DEFINITION;
    }


    builder.error(GroovyBundle.message("indentifier.or.string.literal.expected"));
//    vdMarker.rollbackTo();
    return WRONGWAY;

  }

  private static boolean parseVariableDeclarator(PsiBuilder builder) {
    if (!(ParserUtils.getToken(builder, mIDENT))) {
      return false;
    }

    if (ParserUtils.getToken(builder, mASSIGN)) {
      ParserUtils.getToken(builder, mNLS);
      if (WRONGWAY.equals(AssignmentExpression.parse(builder))) {
        return false;
      }
    }

    return true;
  }
}
