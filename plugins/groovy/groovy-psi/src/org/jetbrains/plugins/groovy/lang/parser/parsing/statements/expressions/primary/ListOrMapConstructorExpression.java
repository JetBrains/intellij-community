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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.primary;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arguments.ArgumentList;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @author ilyas
 */
public class ListOrMapConstructorExpression {

  public static GroovyElementType parse(PsiBuilder builder, GroovyParser parser) {
    PsiBuilder.Marker marker = builder.mark();
    if (!ParserUtils.getToken(builder, GroovyTokenTypes.mLBRACK, GroovyBundle.message("lbrack.expected"))) {
      marker.drop();
      return GroovyElementTypes.WRONGWAY;
    }
    if (ParserUtils.getToken(builder, GroovyTokenTypes.mRBRACK)) {
      marker.done(GroovyElementTypes.LIST_OR_MAP);
      return GroovyElementTypes.LIST_OR_MAP;
    } else if (ParserUtils.getToken(builder, GroovyTokenTypes.mCOLON)) {
      ParserUtils.getToken(builder, GroovyTokenTypes.mRBRACK, GroovyBundle.message("rbrack.expected"));
    } else {
      ArgumentList.parseArgumentList(builder, GroovyTokenTypes.mRBRACK, parser);
      ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
      ParserUtils.getToken(builder, GroovyTokenTypes.mRBRACK, GroovyBundle.message("rbrack.expected"));
    }

    marker.done(GroovyElementTypes.LIST_OR_MAP);
    return GroovyElementTypes.LIST_OR_MAP;
  }
}
