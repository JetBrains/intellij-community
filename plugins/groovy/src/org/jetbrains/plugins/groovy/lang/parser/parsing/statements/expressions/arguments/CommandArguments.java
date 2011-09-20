/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arguments;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.ExpressionStatement;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @author ilyas
 */
public class CommandArguments implements GroovyElementTypes {

  public static boolean parseCommandArguments(PsiBuilder builder, GroovyParser parser) {
    PsiBuilder.Marker marker = builder.mark();
    if (commandArgParse(builder, parser)) {
      while (ParserUtils.getToken(builder, mCOMMA)) {
        ParserUtils.getToken(builder, mNLS);
        if (!commandArgParse(builder, parser)) {
          builder.error(GroovyBundle.message("expression.expected"));
          break;
        }
      }
      marker.done(COMMAND_ARGUMENTS);
      return true;
    }

    marker.drop();
    return false;
  }

  private static boolean commandArgParse(PsiBuilder builder, GroovyParser parser) {
    PsiBuilder.Marker commandMarker = builder.mark();
    if (ArgumentList.argumentLabelStartCheck(builder, parser)) {
      ParserUtils.getToken(builder, mCOLON, GroovyBundle.message("colon.expected"));
      ParserUtils.getToken(builder, mNLS);
      if (!ExpressionStatement.argParse(builder, parser)) {
        builder.error(GroovyBundle.message("expression.expected"));
      }
      commandMarker.done(NAMED_ARGUMENT);
      return true;
    }

    commandMarker.drop();
    return ExpressionStatement.argParse(builder, parser);
  }
}
