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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.types.TypeArguments;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 20.03.2007
 */

public class ClassOrInterfaceType implements GroovyElementTypes
{
  public static GroovyElementType parse(PsiBuilder builder)
  {
    PsiBuilder.Marker internalTypeMarker = builder.mark();
    PsiBuilder.Marker secondInternalTypeMarker;

    if (!ParserUtils.getToken(builder, mIDENT))
    {
      internalTypeMarker.rollbackTo();
      return WRONGWAY;
    }

    TypeArguments.parse(builder);

    secondInternalTypeMarker = internalTypeMarker.precede();
    internalTypeMarker.done(CLASS_INTERFACE_TYPE);
    internalTypeMarker = secondInternalTypeMarker;

    while (ParserUtils.getToken(builder, mDOT))
    {
      if (!ParserUtils.getToken(builder, mIDENT))
      {
        internalTypeMarker.rollbackTo();
        return WRONGWAY;
      }

      TypeArguments.parse(builder);

      secondInternalTypeMarker.done(CLASS_INTERFACE_TYPE);
      secondInternalTypeMarker = internalTypeMarker.precede();
    }

    secondInternalTypeMarker.drop();

    return CLASS_INTERFACE_TYPE;
  }

}
