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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.relational;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arithmetic.ShiftExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.ReferenceElement;
import org.jetbrains.plugins.groovy.lang.parser.parsing.types.TypeSpec;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @author ilyas
 */
public class RelationalExpression {

  public static boolean parse(PsiBuilder builder, GroovyParser parser) {

    PsiBuilder.Marker marker = builder.mark();

    if (ShiftExpression.parse(builder, parser)) {
      if (ParserUtils.getToken(builder, TokenSets.RELATIONS) || getCompositeSign(builder)) {
        ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
        ShiftExpression.parse(builder, parser);
        marker.done(GroovyElementTypes.RELATIONAL_EXPRESSION);
      } else if (GroovyTokenTypes.kINSTANCEOF.equals(builder.getTokenType())) {
        advanceLexerAndParseType(builder);
        marker.done(GroovyElementTypes.INSTANCEOF_EXPRESSION);
      } else if (GroovyTokenTypes.kAS.equals(builder.getTokenType())) {
        advanceLexerAndParseType(builder);
        marker.done(GroovyElementTypes.SAFE_CAST_EXPRESSION);
      } else {
        marker.drop();
      }
      return true;
    } else {
      marker.drop();
      return false;
    }

  }

  private static void advanceLexerAndParseType(PsiBuilder builder) {
    builder.advanceLexer();
    PsiBuilder.Marker rb = builder.mark();
    ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
    if (TypeSpec.parse(builder) == ReferenceElement.ReferenceElementResult.FAIL) {
      rb.rollbackTo();
      builder.error(GroovyBundle.message("type.specification.expected"));
    } else {
      rb.drop();
    }
  }

  /**
   * For composite shift operators like >>>
   *
   * @param builder
   * @return
   */
  private static boolean getCompositeSign(PsiBuilder builder) {
    if (ParserUtils.lookAhead(builder, GroovyTokenTypes.mGT, GroovyTokenTypes.mASSIGN)) {
      PsiBuilder.Marker marker = builder.mark();
      for (int i = 0; i < 2; i++) {
        builder.advanceLexer();
      }
      marker.done(GroovyElementTypes.MORE_OR_EQUALS_SIGN);
      return true;
    } else {
      return false;
    }
  }


}
