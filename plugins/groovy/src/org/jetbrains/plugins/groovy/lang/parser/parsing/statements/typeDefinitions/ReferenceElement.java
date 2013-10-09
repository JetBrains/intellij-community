/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.codeInsight.completion.CompletionInitializationContext;
import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.types.TypeArguments;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.lang.psi.stubs.elements.GrReferenceListElementType;

import static org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.ReferenceElement.ReferenceElementResult.*;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 20.03.2007
 */

public class ReferenceElement implements GroovyElementTypes {
  public static final String DUMMY_IDENTIFIER = CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED; //inserted by completion

  @NotNull
  public static IElementType parseReferenceList(@NotNull PsiBuilder builder,
                                                @NotNull final IElementType startElement,
                                                @NotNull final GrReferenceListElementType<?> clauseType,
                                                @NotNull ClassType type) {
    PsiBuilder.Marker isMarker = builder.mark();

    if (!ParserUtils.getToken(builder, startElement)) {
      if (clauseType == IMPLEMENTS_CLAUSE && (type == ClassType.INTERFACE || type == ClassType.ANNOTATION) ||
          clauseType == EXTENDS_CLAUSE && type == ClassType.ENUM ||
          type == ClassType.ANNOTATION) {
        isMarker.rollbackTo();
        return NONE;
      }

      return finish(clauseType, isMarker, null);
    }

    PsiBuilder.Marker space = builder.mark();
    ParserUtils.getToken(builder, mNLS);

    if (parseReferenceElement(builder) == FAIL) {
      return finish(clauseType, isMarker, space);
    }
    else {
      space.drop();
    }

    while (ParserUtils.getToken(builder, mCOMMA)) {
      space = builder.mark();
      ParserUtils.getToken(builder, mNLS);

      if (parseReferenceElement(builder) == FAIL) {
        return finish(clauseType, isMarker, space);
      }
      else {
        space.drop();
      }
    }

    return finish(clauseType, isMarker, null);
  }

  @NotNull
  private static GrReferenceListElementType<?> finish(@NotNull GrReferenceListElementType<?> clauseType,
                                                      @NotNull PsiBuilder.Marker isMarker,
                                                      @Nullable PsiBuilder.Marker space) {
    if (space != null) space.rollbackTo();
    isMarker.done(clauseType);
    return clauseType;
  }

  public enum ReferenceElementResult {
    IDENTIFIER, PATH_REF, REF_WITH_TYPE_PARAMS, FAIL
  }

  public static ReferenceElementResult parseForImport(@NotNull PsiBuilder builder) {
    return parse(builder, false, false, true, false, false);
  }

  public static ReferenceElementResult parseForPackage(@NotNull PsiBuilder builder) {
    return parse(builder, false, false, false, false, false);
  }

  
  //it doesn't important first letter of identifier of ThrowClause, of Annotation, of new Expression, of implements, extends, superclass clauses
  public static ReferenceElementResult parseReferenceElement(@NotNull PsiBuilder builder) {
    return parseReferenceElement(builder, false, true);
  }

  public static ReferenceElementResult parseReferenceElement(@NotNull PsiBuilder builder, boolean isUpperCase, final boolean expressionPossible) {
    return parse(builder, isUpperCase, true, false, false, expressionPossible);
  }

  public static ReferenceElementResult parse(@NotNull PsiBuilder builder,
                                             boolean checkUpperCase,
                                             boolean parseTypeArgs,
                                             boolean forImport,
                                             boolean allowDiamond,
                                             boolean expressionPossible) {
    PsiBuilder.Marker internalTypeMarker = builder.mark();

    String lastIdentifier = builder.getTokenText();

    if (!ParserUtils.getToken(builder, TokenSets.CODE_REFERENCE_ELEMENT_NAME_TOKENS)) {
      internalTypeMarker.rollbackTo();
      return FAIL;
    }

    boolean hasTypeArguments = false;
    if (parseTypeArgs) {
      hasTypeArguments = TypeArguments.parseTypeArguments(builder, expressionPossible, allowDiamond);
    }

    internalTypeMarker.done(REFERENCE_ELEMENT);
    internalTypeMarker = internalTypeMarker.precede();

    boolean hasDots = builder.getTokenType() == mDOT;

    while (builder.getTokenType() == mDOT) {

      if ((ParserUtils.lookAhead(builder, mDOT, mSTAR) || ParserUtils.lookAhead(builder, mDOT, mNLS, mSTAR)) && forImport) {
        internalTypeMarker.drop();
        return PATH_REF;
      }

      ParserUtils.getToken(builder, mDOT);

      if (forImport) {
        ParserUtils.getToken(builder, mNLS);
      }

      lastIdentifier = builder.getTokenText();

      if (!ParserUtils.getToken(builder, TokenSets.CODE_REFERENCE_ELEMENT_NAME_TOKENS)) {
        internalTypeMarker.rollbackTo();
        return FAIL;
      }

      if (parseTypeArgs) {
        hasTypeArguments = TypeArguments.parseTypeArguments(builder, expressionPossible, allowDiamond) || hasTypeArguments;
      }

      internalTypeMarker.done(REFERENCE_ELEMENT);
      internalTypeMarker = internalTypeMarker.precede();
    }

    if (lastIdentifier == null) {
      //eof
      return FAIL;
    }

    char firstChar = lastIdentifier.charAt(0);
    if (checkUpperCase) {
      if (!Character.isUpperCase(firstChar) || DUMMY_IDENTIFIER.equals(lastIdentifier)) { //hack to make completion work
        internalTypeMarker.rollbackTo();
        return FAIL;
      }
    }

    internalTypeMarker.drop();

    return hasTypeArguments ? REF_WITH_TYPE_PARAMS :
           hasDots ? PATH_REF :
           IDENTIFIER;
  }

}