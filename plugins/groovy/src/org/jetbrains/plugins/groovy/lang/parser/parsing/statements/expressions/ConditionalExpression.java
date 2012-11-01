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
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @author ilyas
 */
public class ConditionalExpression implements GroovyElementTypes {

  public static boolean parse(PsiBuilder builder, GroovyParser parser) {

    PsiBuilder.Marker marker = builder.mark();
    if (BinaryExpression.parseLogicalExpression(builder, parser)) {
      parseAfterCondition(builder, parser, marker);
      return true;
    }
    else {
      marker.drop();
      return false;
    }
  }

  public static void parseAfterCondition(PsiBuilder builder, GroovyParser parser, PsiBuilder.Marker marker) {
    if (ParserUtils.getToken(builder, mQUESTION)) {
      ParserUtils.getToken(builder, mNLS);
      if (!AssignmentExpression.parse(builder, parser)) {
        builder.error(GroovyBundle.message("expression.expected"));
      }

      if (ParserUtils.lookAhead(builder, mNLS, mCOLON)) {
        ParserUtils.getToken(builder, mNLS);
      }
      if (ParserUtils.getToken(builder, mCOLON, GroovyBundle.message("colon.expected"))) {
        ParserUtils.getToken(builder, mNLS);
        parse(builder, parser);
      }
      marker.done(CONDITIONAL_EXPRESSION);
    }
    else if (ParserUtils.getToken(builder, mELVIS)) {
      ParserUtils.getToken(builder, mNLS);
      if (!parse(builder, parser)) {
        builder.error(GroovyBundle.message("expression.expected"));
      }
      marker.done(ELVIS_EXPRESSION);
    }
    else {
      marker.drop();
    }
  }
}