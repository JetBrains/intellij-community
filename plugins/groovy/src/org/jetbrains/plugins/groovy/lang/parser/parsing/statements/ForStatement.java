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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.modifiers.Modifiers;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.blocks.OpenOrClosableBlock;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.declaration.Declaration;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.declaration.DeclarationStart;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.StrictContextExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arithmetic.ShiftExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.types.TypeSpec;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @autor: ilyas
 */
public class ForStatement implements GroovyElementTypes {

  public static GroovyElementType parse(PsiBuilder builder) {
    PsiBuilder.Marker marker = builder.mark();

    ParserUtils.getToken(builder, kFOR);
    if (!ParserUtils.getToken(builder, mLPAREN, GroovyBundle.message("lparen.expected"))) {
      marker.done(FOR_STATEMENT);
      return FOR_STATEMENT;
    }
    if (WRONGWAY.equals(forClauseParse(builder))) {
      builder.error(GroovyBundle.message("for.clause.expected"));
      marker.done(FOR_STATEMENT);
      return FOR_STATEMENT;
    }
    if (ParserUtils.lookAhead(builder, mNLS, mRPAREN)) {
      ParserUtils.getToken(builder, mNLS);
    }
    if (!ParserUtils.getToken(builder, mRPAREN, GroovyBundle.message("rparen.expected"))) {
      while (!builder.eof() && !mNLS.equals(builder.getTokenType()) && !mRPAREN.equals(builder.getTokenType())){
        builder.advanceLexer();
      }
      marker.done(FOR_STATEMENT);
      return FOR_STATEMENT;
    }

    PsiBuilder.Marker warn = builder.mark();
    if (ParserUtils.lookAhead(builder, mNLS)) {
      ParserUtils.getToken(builder, mNLS);
    }

    GroovyElementType result;
    if (mLCURLY.equals(builder.getTokenType())) {
      result = OpenOrClosableBlock.parseOpenBlock(builder);
    } else {
      result = Statement.parse(builder);
    }
    if (result.equals(WRONGWAY) || result.equals(IMPORT_STATEMENT)) {
      warn.rollbackTo();
      builder.error(GroovyBundle.message("expression.expected"));
      marker.done(FOR_STATEMENT);
      return FOR_STATEMENT;
    } else {
      warn.drop();
      marker.done(FOR_STATEMENT);
      return FOR_STATEMENT;
    }
  }

  private static GroovyElementType forClauseParse(PsiBuilder builder) {
    ParserUtils.getToken(builder, mNLS);
    GroovyElementType result = forInClauseParse(builder);
    if (!WRONGWAY.equals(result)) {
      return result;
    } else {
      return tradForClauseParse(builder);
    }
  }

  private static GroovyElementType tradForClauseParse(PsiBuilder builder) {

    PsiBuilder.Marker marker = builder.mark();

    if (ParserUtils.getToken(builder, mSEMI) ||
            (!Declaration.parse(builder, false, false).equals(WRONGWAY) &&
                    ParserUtils.getToken(builder, mSEMI))) {
      StrictContextExpression.parse(builder);
      ParserUtils.getToken(builder, mSEMI, GroovyBundle.message("semi.expected"));
      ParserUtils.getToken(builder, mNLS);
      if (!mRPAREN.equals(builder.getTokenType())) {
        controlExpressionListParse(builder);
      }
    } else {
      marker.rollbackTo();
      marker = builder.mark();
      controlExpressionListParse(builder);
      ParserUtils.getToken(builder, mSEMI, GroovyBundle.message("semi.expected"));
      StrictContextExpression.parse(builder);
      ParserUtils.getToken(builder, mSEMI, GroovyBundle.message("semi.expected"));
      ParserUtils.getToken(builder, mNLS);
      if (!mRPAREN.equals(builder.getTokenType())) {
        controlExpressionListParse(builder);
      }
    }

    marker.done(FOR_TRADITIONAL_CLAUSE);
    return FOR_TRADITIONAL_CLAUSE;
  }

  /**
   * Parses list of control expression in for condition
   */
  private static GroovyElementType controlExpressionListParse(PsiBuilder builder) {

    GroovyElementType result = StrictContextExpression.parse(builder);
    if (result.equals(WRONGWAY)) {
      return WRONGWAY;
    }
    while (mCOMMA.equals(builder.getTokenType()) || !result.equals(WRONGWAY)) {

      if (ParserUtils.lookAhead(builder, mCOMMA, mNLS, mRPAREN) ||
              ParserUtils.lookAhead(builder, mCOMMA, mRPAREN)) {
        ParserUtils.getToken(builder, mCOMMA);
        builder.error(GroovyBundle.message("expression.expected"));
      } else {
        ParserUtils.getToken(builder, mCOMMA);
      }
      ParserUtils.getToken(builder, mNLS);
      result = StrictContextExpression.parse(builder);
      if (result.equals(WRONGWAY)) {
        ParserUtils.getToken(builder, mNLS);
        if (!mRPAREN.equals(builder.getTokenType()) &&
                !mSEMI.equals(builder.getTokenType())) {
          builder.error(GroovyBundle.message("expression.expected"));
        }
        if (!mRPAREN.equals(builder.getTokenType()) &&
                !mSEMI.equals(builder.getTokenType()) &&
                !mCOMMA.equals(builder.getTokenType()) &&
                !mNLS.equals(builder.getTokenType())) {
          builder.advanceLexer();
        }
      }
    }

    return FOR_TRADITIONAL_CLAUSE;
  }

  /**
   * Parses Groovy-style 'in' clause
   */
  private static GroovyElementType forInClauseParse(PsiBuilder builder) {

    PsiBuilder.Marker marker = builder.mark();

    PsiBuilder.Marker declMarker = builder.mark();

    if (ParserUtils.lookAhead(builder, mIDENT, kIN)) {
      ParserUtils.eatElement(builder, PARAMETER);
      declMarker.drop();
      ParserUtils.getToken(builder, kIN);
      if (WRONGWAY.equals(ShiftExpression.parse(builder))) {
        builder.error(GroovyBundle.message("expression.expected"));
      }
      marker.done(FOR_IN_CLAUSE);
      return FOR_IN_CLAUSE;
    }

    if (DeclarationStart.parse(builder)) {
      if (!Modifiers.parse(builder).equals(WRONGWAY)) {
        TypeSpec.parse(builder);
        return singleDeclNoInitParse(builder, marker, declMarker);
      }
    }

    if (!WRONGWAY.equals(TypeSpec.parse(builder))) {
      return singleDeclNoInitParse(builder, marker, declMarker);
    }

    declMarker.drop();
    marker.drop();
    return WRONGWAY;
  }

  private static GroovyElementType singleDeclNoInitParse(PsiBuilder builder,
                                                         PsiBuilder.Marker marker,
                                                         PsiBuilder.Marker declMarker) {
    if (ParserUtils.getToken(builder, mIDENT)) {
      if (kIN.equals(builder.getTokenType())) {
        declMarker.done(VARIABLE);
        ParserUtils.getToken(builder, kIN);
        if (WRONGWAY.equals(ShiftExpression.parse(builder))) {
          builder.error(GroovyBundle.message("expression.expected"));
        }
        marker.done(FOR_IN_CLAUSE);
        return FOR_IN_CLAUSE;
      } else {
        marker.rollbackTo();
        return WRONGWAY;
      }
    } else {
      declMarker.drop();
      marker.rollbackTo();
      return WRONGWAY;
    }
  }

}
