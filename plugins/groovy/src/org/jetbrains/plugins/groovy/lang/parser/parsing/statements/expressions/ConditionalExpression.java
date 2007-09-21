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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.logical.LogicalOrExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @author ilyas
 */
public class ConditionalExpression implements GroovyElementTypes {

  public static GroovyElementType parse(PsiBuilder builder) {

    PsiBuilder.Marker marker = builder.mark();
    GroovyElementType result = LogicalOrExpression.parse(builder);
    if (!result.equals(WRONGWAY)) {
      if (ParserUtils.getToken(builder, mQUESTION)) {
        result = CONDITIONAL_EXPRESSION;
        ParserUtils.getToken(builder, mNLS);
        GroovyElementType res = AssignmentExpression.parse(builder);
        if (res.equals(WRONGWAY)) {
          builder.error(GroovyBundle.message("expression.expected"));
        }
        if (ParserUtils.getToken(builder, mCOLON, GroovyBundle.message("colon.expected"))) {
          ParserUtils.getToken(builder, mNLS);
          parse(builder);
        }
        marker.done(CONDITIONAL_EXPRESSION);
      } else if (ParserUtils.getToken(builder, mELVIS)) {
        result = ELVIS_EXPRESSION;
        ParserUtils.getToken(builder, mNLS);
        GroovyElementType res = parse(builder);
        if (res.equals(WRONGWAY)) {
          builder.error(GroovyBundle.message("expression.expected"));
        }
        marker.done(ELVIS_EXPRESSION);
      } else {
        marker.drop();
      }
    } else {
      marker.drop();
    }
    return result;


  }

}