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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arithmetic.MultiplicativeExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arithmetic.UnaryExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.regex.RegexExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.relational.RelationalExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @author peter
 */
public class BinaryExpression {
  private static final BinaryExpression[] ourLogicalExpressions = {
    new BinaryExpression(GroovyElementTypes.LOGICAL_OR_EXPRESSION, GroovyTokenTypes.mLOR),
    new BinaryExpression(GroovyElementTypes.LOGICAL_AND_EXPRESSION, GroovyTokenTypes.mLAND),
    new BinaryExpression(GroovyElementTypes.INCLUSIVE_OR_EXPRESSION, GroovyTokenTypes.mBOR),
    new BinaryExpression(GroovyElementTypes.EXCLUSIVE_OR_EXPRESSION, GroovyTokenTypes.mBXOR),
    new BinaryExpression(GroovyElementTypes.AND_EXPRESSION, GroovyTokenTypes.mBAND) {
      @Override
      protected boolean parseNext(PsiBuilder builder, GroovyParser parser, int order) {
        return RegexExpression.parse(builder, parser);
      }
    }
  };

  public static final BinaryExpression EQUALITY = new BinaryExpression(GroovyElementTypes.EQUALITY_EXPRESSION, GroovyTokenTypes.mEQUAL,
                                                                       GroovyTokenTypes.mNOT_EQUAL, GroovyTokenTypes.mCOMPARE_TO) {
    @Override
    protected boolean parseNext(PsiBuilder builder, GroovyParser parser, int order) {
      return RelationalExpression.parse(builder, parser);
    }
  };
  public static final BinaryExpression ADDITIVE = new BinaryExpression(GroovyElementTypes.ADDITIVE_EXPRESSION, GroovyTokenTypes.mPLUS,
                                                                       GroovyTokenTypes.mMINUS) {
    @Override
    protected boolean parseNext(PsiBuilder builder, GroovyParser parser, int order) {
      return MultiplicativeExpression.parse(builder, parser);
    }
  };
  public static final BinaryExpression POWER = new BinaryExpression(GroovyElementTypes.POWER_EXPRESSION, GroovyTokenTypes.mSTAR_STAR) {
    @Override
    protected boolean parseNext(PsiBuilder builder, GroovyParser parser, int order) {
      return UnaryExpression.parse(builder, parser);
    }
  };

  private final IElementType myCompositeType;
  private final TokenSet myOpTokens;

  public BinaryExpression(IElementType compositeType, IElementType... opTokens) {
    myCompositeType = compositeType;
    myOpTokens = TokenSet.create(opTokens);
  }

  public static boolean parseLogicalExpression(PsiBuilder builder, GroovyParser parser) {
    return ourLogicalExpressions[0].parseBinary(builder, parser);
  }

  protected boolean parseNext(PsiBuilder builder, GroovyParser parser, int order) {
    return ourLogicalExpressions[order + 1].parseBinary(builder, parser, order + 1);
  }

  public final boolean parseBinary(PsiBuilder builder, GroovyParser parser) {
    return parseBinary(builder, parser, 0);
  }

  private boolean parseBinary(PsiBuilder builder, GroovyParser parser, int order) {
    PsiBuilder.Marker marker = builder.mark();

    if (parseNext(builder, parser, order)) {
      if (ParserUtils.getToken(builder, myOpTokens)) {
        ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
        if (!parseNext(builder, parser, order)) {
          builder.error(GroovyBundle.message("expression.expected"));
        }
        PsiBuilder.Marker newMarker = marker.precede();
        marker.done(myCompositeType);
        if (myOpTokens.contains(builder.getTokenType())) {
          subParse(builder, newMarker, parser, order);
        } else {
          newMarker.drop();
        }
      } else {
        marker.drop();
      }
      return true;
    } else {
      marker.drop();
      return false;
    }
  }

  private void subParse(PsiBuilder builder, PsiBuilder.Marker marker, GroovyParser parser, int order) {
    builder.advanceLexer();
    ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
    if (!parseNext(builder, parser, order)) {
      builder.error(GroovyBundle.message("expression.expected"));
    }
    PsiBuilder.Marker newMarker = marker.precede();
    marker.done(myCompositeType);
    if (myOpTokens.contains(builder.getTokenType())) {
      subParse(builder, newMarker, parser, order);
    } else {
      newMarker.drop();
    }
  }
}
