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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.relational;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arithmetic.ShiftExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.types.TypeSpec;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @author ilyas
 */
public class RelationalExpression implements GroovyElementTypes {

  private static TokenSet RELATIONS = TokenSet.create(
          mLT,
          mGT,
          mLE,
          mGE,
          kIN
  );

  public static GroovyElementType parse(PsiBuilder builder) {

    PsiBuilder.Marker marker = builder.mark();

    GroovyElementType result = ShiftExpression.parse(builder);
    if (!result.equals(WRONGWAY)) {
      if (ParserUtils.getToken(builder, RELATIONS) ||
              getCompositeSign(builder)) {
        result = RELATIONAL_EXPRESSION;
        ParserUtils.getToken(builder, mNLS);
        ShiftExpression.parse(builder);
        marker.done(RELATIONAL_EXPRESSION);
      } else if (kINSTANCEOF.equals(builder.getTokenType())) {
        advanceLexerAndParseType(builder);
        marker.done(INSTANCEOF_EXPRESSION);
      } else if (kAS.equals(builder.getTokenType())) {
        advanceLexerAndParseType(builder);
        marker.done(SAFE_CAST_EXPRESSION);
      } else {
        marker.drop();
      }
    } else {
      marker.drop();
    }

    return result;
  }

  private static void advanceLexerAndParseType(PsiBuilder builder) {
    builder.advanceLexer();
    PsiBuilder.Marker rb = builder.mark();
    ParserUtils.getToken(builder, mNLS);
    if (WRONGWAY.equals(TypeSpec.parse(builder))) {
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
    if (ParserUtils.lookAhead(builder, mGT, mASSIGN)) {
      PsiBuilder.Marker marker = builder.mark();
      for (int i = 0; i < 2; i++) {
        builder.advanceLexer();
      }
      marker.done(COMPOSITE_SHIFT_SIGN);
      return true;
    } else {
      return false;
    }
  }


}
