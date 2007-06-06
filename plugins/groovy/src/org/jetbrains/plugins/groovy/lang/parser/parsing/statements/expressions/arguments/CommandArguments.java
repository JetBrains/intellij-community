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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arguments;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.ExpressionStatement;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @author ilyas
 */
public class CommandArguments implements GroovyElementTypes {

  public static GroovyElementType parse(PsiBuilder builder) {

    PsiBuilder.Marker marker = builder.mark();
    GroovyElementType result = commandArgParse(builder);
    if (!result.equals(WRONGWAY)) {
      while (builder.getTokenType() == mCOMMA && !result.equals(WRONGWAY)) {
        ParserUtils.getToken(builder, mCOMMA);
        ParserUtils.getToken(builder, mNLS);
        result = commandArgParse(builder);
        if (result.equals(WRONGWAY)) {
          builder.error(GroovyBundle.message("expression.expected"));
        }
      }
      marker.done(COMMAND_ARGUMENTS);
    } else {
      marker.drop();
    }

    return result;
  }

  private static GroovyElementType commandArgParse(PsiBuilder builder){
    PsiBuilder.Marker commandMarker = builder.mark();
    if (ArgumentList.argumentLabelStartCheck(builder)){
      ParserUtils.getToken(builder, mCOLON, GroovyBundle.message("colon.expected"));
      GroovyElementType result = ExpressionStatement.argParse(builder);
      if (result.equals(WRONGWAY)){
        commandMarker.error(GroovyBundle.message("expression.expected"));
      } else {
        commandMarker.done(COMMAND_ARGUMENT);
      }
      return COMMAND_ARGUMENT;
    } else {
      commandMarker.drop();
      return ExpressionStatement.parse(builder);
    }
  }
}