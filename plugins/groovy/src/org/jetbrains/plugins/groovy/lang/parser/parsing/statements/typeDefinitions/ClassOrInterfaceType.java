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
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.PsiBuilder;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 20.03.2007
 */

public class ClassOrInterfaceType implements GroovyElementTypes {
  public static GroovyElementType parse(PsiBuilder builder) {
    //todo: add cases

    PsiBuilder.Marker citMarker = builder.mark();

    if (!ParserUtils.getToken(builder, mIDENT)){
      citMarker.rollbackTo();
      return WRONGWAY;
    }

    citMarker.done(CLASS_INTERFACE_TYPE);    
    return CLASS_INTERFACE_TYPE;
  }

  /**
   * Strict parsing. In case of any convergence returns WRONGWAY
   *
   * @param builder
   * @return
   */
  // TODO Implement it, please in accordance with javadoc above 
  public static GroovyElementType parseStrict(PsiBuilder builder){
    PsiBuilder.Marker citMarker = builder.mark();
    if (!ParserUtils.getToken(builder, mIDENT)){
      citMarker.rollbackTo();
      return WRONGWAY;
    }

    citMarker.done(CLASS_INTERFACE_TYPE);
    return CLASS_INTERFACE_TYPE;
  }
}
