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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arithmetic;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @author ilyas
 */
public class PowerExpressionNotPlusMinus implements GroovyElementTypes {

  public static boolean parse(PsiBuilder builder) {

    PsiBuilder.Marker marker = builder.mark();

    if (UnaryExpressionNotPlusMinus.parse(builder)) {
      if (ParserUtils.getToken(builder, mSTAR_STAR)) {
        ParserUtils.getToken(builder, mNLS);
        if (!UnaryExpression.parse(builder)) {
          builder.error(GroovyBundle.message("expression.expected"));
        }
        PsiBuilder.Marker newMarker = marker.precede();
        marker.done(POWER_EXPRESSION_SIMPLE);
        if (mSTAR_STAR.equals(builder.getTokenType())) {
          subParse(builder, newMarker);
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

  private static void subParse(PsiBuilder builder, PsiBuilder.Marker marker) {
    ParserUtils.getToken(builder, mSTAR_STAR);
    ParserUtils.getToken(builder, mNLS);
    if (!UnaryExpression.parse(builder)) {
      builder.error(GroovyBundle.message("expression.expected"));
    }
    PsiBuilder.Marker newMarker = marker.precede();
    marker.done(POWER_EXPRESSION_SIMPLE);
    if (mSTAR_STAR.equals(builder.getTokenType())) {
      subParse(builder, newMarker);
    } else {
      newMarker.drop();
    }
  }


}

