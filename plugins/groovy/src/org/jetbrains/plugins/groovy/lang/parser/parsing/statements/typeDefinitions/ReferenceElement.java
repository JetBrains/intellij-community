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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.types.TypeArguments;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

import static org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.ReferenceElement.ReferenceElementResult.fail;
import static org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.ReferenceElement.ReferenceElementResult.mayBeType;
import static org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.ReferenceElement.ReferenceElementResult.mustBeType;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 20.03.2007
 */

public class ReferenceElement implements GroovyElementTypes {
  public static final String DUMMY_IDENTIFIER = "IntellijIdeaRulezzz"; //inserted by completion

  public enum ReferenceElementResult {
    mayBeType, mustBeType, fail
  }

  public static ReferenceElementResult parseForImport(PsiBuilder builder) {
    return parse(builder, false, false, true, false);
  }

  public static ReferenceElementResult parseForPackage(PsiBuilder builder) {
    return parse(builder, false, false, false, true);
  }

  
  //it doesn't important first letter of identifier of ThrowClause, of Annotation, of new Expresion, of implements, extends, superclass clauses
  public static ReferenceElementResult parseReferenceElement(PsiBuilder builder) {
    return parse(builder, false, true, false, false);
  }

  public static ReferenceElementResult parseReferenceElement(PsiBuilder builder, boolean isUpperCase) {
    return parse(builder, isUpperCase, true, false, false);
  }

//  public static GroovyElementType parse(PsiBuilder builder) {
//    return parse(builder, false, false, false, false);
//  }

  public static ReferenceElementResult parse(PsiBuilder builder, boolean checkUpperCase, boolean parseTypeArgs, boolean forImport, boolean forPackage) {
    PsiBuilder.Marker internalTypeMarker = builder.mark();

//    char firstChar;
//    if (builder.getTokenText() != null) firstChar = builder.getTokenText().charAt(0);
//    else return WRONGWAY;

//    if (checkUpperCase && !Character.isUpperCase(firstChar)) {
//      internalTypeMarker.rollbackTo();
//      return WRONGWAY;
//    }

    String lastIdentifier = builder.getTokenText();

    if (!ParserUtils.getToken(builder, mIDENT)) {
      internalTypeMarker.rollbackTo();
      return fail;
    }
    boolean hasTypeArguments = false;
    if (parseTypeArgs) {
      hasTypeArguments = TypeArguments.parse(builder);
    }

    internalTypeMarker.done(REFERENCE_ELEMENT);
    internalTypeMarker = internalTypeMarker.precede();

    while (mDOT.equals(builder.getTokenType())) {

      if ((ParserUtils.lookAhead(builder, mDOT, mSTAR) ||
          ParserUtils.lookAhead(builder, mDOT, mNLS, mSTAR)) &&
          forImport) {
        internalTypeMarker.drop();
        return hasTypeArguments? mustBeType : mayBeType;
      }

      ParserUtils.getToken(builder, mDOT);

      if (forImport) {
        ParserUtils.getToken(builder, mNLS);
      }

      lastIdentifier = builder.getTokenText();

      if (!ParserUtils.getToken(builder, mIDENT)) {
        internalTypeMarker.rollbackTo();
        return fail;
      }

      hasTypeArguments = TypeArguments.parse(builder) || hasTypeArguments;

      internalTypeMarker.done(REFERENCE_ELEMENT);
      internalTypeMarker = internalTypeMarker.precede();
    }

    char firstChar;
    if (lastIdentifier != null) firstChar = lastIdentifier.charAt(0);
    else return fail;

    if (checkUpperCase && (!Character.isUpperCase(firstChar) || DUMMY_IDENTIFIER.equals(lastIdentifier))) { //hack to make completion work
      internalTypeMarker.rollbackTo();
      return fail;
    }

    //    internalTypeMarker.done(TYPE_ELEMENT);
    internalTypeMarker.drop();
    return hasTypeArguments ? mustBeType : mayBeType;
  }

}