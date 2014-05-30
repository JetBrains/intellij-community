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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions;

import com.intellij.codeInsight.completion.CompletionUtilCore;
import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.types.TypeArguments;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.lang.psi.stubs.elements.GrReferenceListElementType;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 20.03.2007
 */

public class ReferenceElement {
  public static final String DUMMY_IDENTIFIER = CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED; //inserted by completion

  @NotNull
  public static IElementType parseReferenceList(@NotNull PsiBuilder builder,
                                                @NotNull final IElementType startElement,
                                                @NotNull final GrReferenceListElementType<?> clauseType,
                                                @NotNull ClassType type) {
    PsiBuilder.Marker isMarker = builder.mark();

    if (!ParserUtils.getToken(builder, startElement)) {
      if (clauseType == GroovyElementTypes.IMPLEMENTS_CLAUSE && (type == ClassType.INTERFACE || type == ClassType.ANNOTATION) ||
          clauseType == GroovyElementTypes.EXTENDS_CLAUSE && type == ClassType.ENUM ||
          type == ClassType.ANNOTATION) {
        isMarker.rollbackTo();
        return GroovyElementTypes.NONE;
      }

      return finish(builder, clauseType, isMarker, null, null);
    }

    PsiBuilder.Marker space = builder.mark();
    ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);

    if (parseReferenceElement(builder) == ReferenceElementResult.FAIL) {
      return finish(builder, clauseType, isMarker, space, GroovyBundle.message("identifier.expected"));
    }
    else {
      space.drop();
    }

    while (ParserUtils.getToken(builder, GroovyTokenTypes.mCOMMA)) {
      space = builder.mark();
      ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);

      if (parseReferenceElement(builder) == ReferenceElementResult.FAIL) {
        return finish(builder, clauseType, isMarker, space, GroovyBundle.message("identifier.expected"));
      }
      else {
        space.drop();
      }
    }

    return finish(builder, clauseType, isMarker, null, null);
  }

  @NotNull
  private static GrReferenceListElementType<?> finish(@NotNull PsiBuilder builder,
                                                      @NotNull GrReferenceListElementType<?> clauseType,
                                                      @NotNull PsiBuilder.Marker isMarker,
                                                      @Nullable PsiBuilder.Marker space,
                                                      @Nullable String error) {
    if (space != null) space.rollbackTo();
    if (error != null) builder.error(error);
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
    return parse(builder, false, false, true, false, false);
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
                                             boolean lineFeedAllowed,
                                             boolean allowDiamond,
                                             boolean expressionPossible) {
    PsiBuilder.Marker internalTypeMarker = builder.mark();

    String lastIdentifier = builder.getTokenText();

    if (!ParserUtils.getToken(builder, TokenSets.CODE_REFERENCE_ELEMENT_NAME_TOKENS)) {
      internalTypeMarker.rollbackTo();
      return ReferenceElementResult.FAIL;
    }

    boolean hasTypeArguments = false;
    if (parseTypeArgs && TypeArguments.parseTypeArguments(builder, expressionPossible, allowDiamond)) {
      hasTypeArguments = true;
    }

    internalTypeMarker.done(GroovyElementTypes.REFERENCE_ELEMENT);
    internalTypeMarker = internalTypeMarker.precede();

    boolean hasDots = builder.getTokenType() == GroovyTokenTypes.mDOT;

    while (builder.getTokenType() == GroovyTokenTypes.mDOT) {

      if ((ParserUtils.lookAhead(builder, GroovyTokenTypes.mDOT, GroovyTokenTypes.mSTAR) || ParserUtils.lookAhead(builder,
                                                                                                                  GroovyTokenTypes.mDOT,
                                                                                                                  GroovyTokenTypes.mNLS,
                                                                                                                  GroovyTokenTypes.mSTAR)) && lineFeedAllowed) {
        internalTypeMarker.drop();
        return ReferenceElementResult.PATH_REF;
      }

      ParserUtils.getToken(builder, GroovyTokenTypes.mDOT);

      if (lineFeedAllowed) {
        ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
      }

      lastIdentifier = builder.getTokenText();

      if (!ParserUtils.getToken(builder, TokenSets.CODE_REFERENCE_ELEMENT_NAME_TOKENS)) {
        if (TokenSets.REFERENCE_NAME_PREFIXES.contains(builder.getTokenType())) {
          internalTypeMarker.rollbackTo();
          return ReferenceElementResult.FAIL;
        }
        builder.error(GroovyBundle.message("identifier.expected"));
        internalTypeMarker.done(GroovyElementTypes.REFERENCE_ELEMENT);
        return ReferenceElementResult.PATH_REF;
      }

      if (parseTypeArgs && TypeArguments.parseTypeArguments(builder, expressionPossible, allowDiamond)) {
        hasTypeArguments = true;
      }

      internalTypeMarker.done(GroovyElementTypes.REFERENCE_ELEMENT);
      internalTypeMarker = internalTypeMarker.precede();
    }

    if (lastIdentifier == null) {
      //eof
      return ReferenceElementResult.FAIL;
    }

    char firstChar = lastIdentifier.charAt(0);
    if (checkUpperCase) {
      if (!Character.isUpperCase(firstChar) || DUMMY_IDENTIFIER.equals(lastIdentifier)) { //hack to make completion work
        internalTypeMarker.rollbackTo();
        return ReferenceElementResult.FAIL;
      }
    }

    internalTypeMarker.drop();

    return hasTypeArguments ? ReferenceElementResult.REF_WITH_TYPE_PARAMS :
           hasDots ? ReferenceElementResult.PATH_REF :
           ReferenceElementResult.IDENTIFIER;
  }

}
