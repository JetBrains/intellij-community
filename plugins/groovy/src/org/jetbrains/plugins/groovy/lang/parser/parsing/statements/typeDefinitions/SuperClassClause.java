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

import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.GroovyBundle;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.PsiBuilder;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 16.03.2007
 */
public class SuperClassClause implements GroovyElementTypes {
  public static IElementType parse(PsiBuilder builder) {
    PsiBuilder.Marker sccMarker = builder.mark();

    if (!ParserUtils.getToken(builder, kEXTENDS)){
      sccMarker.rollbackTo();
      return NONE;
    }

    ParserUtils.getToken(builder, mNLS);

    if (WRONGWAY.equals(ClassOrInterfaceType.parse(builder))){
      sccMarker.rollbackTo();
      builder.error(GroovyBundle.message("class.or.interface.type.expected"));
      return WRONGWAY;
    }

    ParserUtils.getToken(builder, mNLS);

    sccMarker.done(EXTENDS_CLAUSE);
    return EXTENDS_CLAUSE;
  }
}
