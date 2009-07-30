/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.members;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 06.04.2007
 */
public class EnumConstants implements GroovyElementTypes {
  public static GroovyElementType parse(PsiBuilder builder, GroovyParser parser) {
    PsiBuilder.Marker enumConstantsMarker = builder.mark();

    if (WRONGWAY.equals(EnumConstant.parse(builder, parser))) {
      return WRONGWAY;
    }

    while (ParserUtils.getToken(builder, mCOMMA)) {
      EnumConstant.parse(builder, parser);
    }

    ParserUtils.getToken(builder, mCOMMA);

    enumConstantsMarker.done(ENUM_CONSTANTS);
    return ENUM_CONSTANTS;
  }
}
