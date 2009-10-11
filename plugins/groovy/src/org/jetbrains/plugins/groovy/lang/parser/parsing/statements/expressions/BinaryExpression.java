/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.*;
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
    new BinaryExpression(LOGICAL_OR_EXPRESSION, mLOR),
    new BinaryExpression(LOGICAL_AND_EXPRESSION, mLAND),
    new BinaryExpression(INCLUSIVE_OR_EXPRESSION, mBOR),
    new BinaryExpression(EXCLUSIVE_OR_EXPRESSION, mBXOR),
    new BinaryExpression(AND_EXPRESSION, mBAND) {
      @Override
      protected boolean parseNext(PsiBuilder builder, GroovyParser parser, int order) {
        return RegexExpression.parse(builder, parser);
      }
    }
  };

  public static final BinaryExpression EQUALITY = new BinaryExpression(EQUALITY_EXPRESSION, mEQUAL, mNOT_EQUAL, mCOMPARE_TO) {
    @Override
    protected boolean parseNext(PsiBuilder builder, GroovyParser parser, int order) {
      return RelationalExpression.parse(builder, parser);
    }
  };
  public static final BinaryExpression ADDITIVE = new BinaryExpression(ADDITIVE_EXPRESSION, mPLUS, mMINUS) {
    @Override
    protected boolean parseNext(PsiBuilder builder, GroovyParser parser, int order) {
      return MultiplicativeExpression.parse(builder, parser);
    }
  };
  public static final BinaryExpression POWER = new BinaryExpression(POWER_EXPRESSION, mSTAR_STAR) {
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
        ParserUtils.getToken(builder, mNLS);
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
    ParserUtils.getToken(builder, mNLS);
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
