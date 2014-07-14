/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.modifiers.Modifiers;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.parameters.ParameterDeclaration;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.ConditionalExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.ExpressionStatement;
import org.jetbrains.plugins.groovy.lang.parser.parsing.types.TypeSpec;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @autor: ilyas
 */
public class ForStatement {

  public static boolean forClauseParse(PsiBuilder builder, GroovyParser parser) {
    ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
    return forInClauseParse(builder, parser) || tradForClauseParse(builder, parser);
  }

  private static boolean tradForClauseParse(PsiBuilder builder, GroovyParser parser) {
    PsiBuilder.Marker marker = builder.mark();

    if (!ParameterDeclaration.parseTraditionalForParameter(builder, parser)) {
      marker.rollbackTo();
      marker = builder.mark();
      ExpressionStatement.argParse(builder, parser);
    }

    ParserUtils.getToken(builder, GroovyTokenTypes.mSEMI, GroovyBundle.message("semi.expected"));
    ExpressionStatement.argParse(builder, parser);
    ParserUtils.getToken(builder, GroovyTokenTypes.mSEMI, GroovyBundle.message("semi.expected"));
    ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
    if (!GroovyTokenTypes.mRPAREN.equals(builder.getTokenType())) {
      ExpressionStatement.argParse(builder, parser);
    }
    marker.done(GroovyElementTypes.FOR_TRADITIONAL_CLAUSE);
    return true;
  }

  /*
   * Parses Groovy-style 'in' clause
   */
  private static boolean forInClauseParse(PsiBuilder builder, GroovyParser parser) {
    PsiBuilder.Marker marker = builder.mark();

    PsiBuilder.Marker paramMarker = builder.mark();

    Modifiers.parse(builder, parser);

    boolean isBuiltInType = TokenSets.BUILT_IN_TYPES.contains(builder.getTokenType());

    PsiBuilder.Marker typeSpec = builder.mark();
    TypeSpec.parseStrict(builder, false);

    if (builder.getTokenType() == GroovyTokenTypes.mIDENT || isBuiltInType) {
      typeSpec.drop();
    }
    else {
      typeSpec.rollbackTo();
    }

    if (TokenSets.FOR_IN_DELIMITERS.contains(builder.getTokenType())) {
      builder.error(GroovyBundle.message("identifier.expected"));
      paramMarker.drop();
    }
    else if (builder.getTokenType() == GroovyTokenTypes.mIDENT) {
      ParserUtils.getToken(builder, GroovyTokenTypes.mIDENT);
      paramMarker.done(GroovyElementTypes.PARAMETER);
    }
    else {
      paramMarker.drop();
      marker.rollbackTo();
      return false;
    }

    if (!ParserUtils.getToken(builder, GroovyTokenTypes.kIN) && !ParserUtils.getToken(builder, GroovyTokenTypes.mCOLON)) {
      marker.rollbackTo();
      return false;
    }

    if (!ConditionalExpression.parse(builder, parser)) {
      builder.error(GroovyBundle.message("expression.expected"));
    }
    marker.done(GroovyElementTypes.FOR_IN_CLAUSE);
    return true;
  }
}
