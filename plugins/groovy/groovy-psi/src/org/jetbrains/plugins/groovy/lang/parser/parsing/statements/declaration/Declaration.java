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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.declaration;

import com.intellij.lang.PsiBuilder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.modifiers.Modifiers;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.ReferenceElement;
import org.jetbrains.plugins.groovy.lang.parser.parsing.types.TypeParameters;
import org.jetbrains.plugins.groovy.lang.parser.parsing.types.TypeSpec;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 14.03.2007
 */

/*
 * Declaration ::= modifiers [TypeSpec] VariableDefinitions
 *                  | TypeSpec VariableDefinitions
 */

public class Declaration {

  public static boolean parse(@NotNull PsiBuilder builder,
                              boolean isInClass,
                              boolean isInAnnotation,
                              @Nullable String typeDefinitionName,
                              @NotNull GroovyParser parser) {
    PsiBuilder.Marker declMarker = builder.mark();
    //allows error messages
    boolean modifiersParsed = Modifiers.parse(builder, parser);

    final boolean methodStart = GroovyTokenTypes.mLT == builder.getTokenType();
    final IElementType type = parseAfterModifiers(builder, isInClass, isInAnnotation, typeDefinitionName, parser, modifiersParsed);
    if (type == GroovyElementTypes.WRONGWAY) {
      if (modifiersParsed && methodStart) {
        declMarker.error(GroovyBundle.message("method.definitions.expected"));
        return false;
      }

      declMarker.rollbackTo();
      if (modifiersParsed) {
        builder.error(GroovyBundle.message("variable.definitions.expected"));
      }

      return false;
    }

    if (type != null) {
      declMarker.done(type);
    } else {
      declMarker.drop();
    }
    return true;
  }

  @Nullable
  public static IElementType parseAfterModifiers(@NotNull PsiBuilder builder,
                                                 boolean isInClass,
                                                 boolean isInAnnotation,
                                                 @Nullable String typeDefinitionName,
                                                 @NotNull GroovyParser parser,
                                                 boolean modifiersParsed) {
    boolean expressionPossible = !isInAnnotation && !isInClass;
    if (modifiersParsed && builder.getTokenType() == GroovyTokenTypes.mLT) {
      return parseDeclarationWithGenerics(builder, isInClass, isInAnnotation, typeDefinitionName, parser, modifiersParsed, expressionPossible);
    }
    else if (modifiersParsed) {
      return parseDeclarationWithoutGenerics(builder, isInClass, isInAnnotation, typeDefinitionName, parser, modifiersParsed, expressionPossible);
    }
    else if (typeDefinitionName != null && ParserUtils.lookAhead(builder, GroovyTokenTypes.mIDENT, GroovyTokenTypes.mLPAREN) && typeDefinitionName.equals(builder.getTokenText())) {
      //parse constructor
      return VariableDefinitions.parseDefinitions(builder, isInClass, isInAnnotation, typeDefinitionName, modifiersParsed, false, parser);
    }
    else {
      return parsePossibleCallExpression(builder, isInClass, isInAnnotation, typeDefinitionName, parser, expressionPossible);
    }
  }

  private static IElementType parsePossibleCallExpression(@NotNull PsiBuilder builder,
                                                          boolean isInClass,
                                                          boolean isInAnnotation,
                                                          @Nullable String typeDefinitionName,
                                                          @NotNull GroovyParser parser,
                                                          boolean expressionPossible) {
    if (isCall(builder)) {
      return GroovyElementTypes.WRONGWAY;
    }

    boolean typeParsed = false;
    if (!ParserUtils.lookAhead(builder, GroovyTokenTypes.mIDENT, GroovyTokenTypes.mLPAREN)) {
      typeParsed = TypeSpec.parse(builder, true, expressionPossible) != ReferenceElement.ReferenceElementResult.FAIL;
      //type specification starts with upper case letter
      if (!typeParsed) {
        return GroovyElementTypes.WRONGWAY;
      }
    }

    IElementType varDef = VariableDefinitions.parseDefinitions(builder, isInClass, isInAnnotation, typeDefinitionName, typeParsed, false, parser);
    if (varDef != GroovyElementTypes.WRONGWAY) {
      return varDef;
    }
    if (isInClass && typeParsed) {
      return null;
    }

    return GroovyElementTypes.WRONGWAY;
  }

  private static boolean isCall(@NotNull PsiBuilder builder) {
    if (builder.eof()) return false;
    if (TokenSets.BUILT_IN_TYPES.contains(builder.getTokenType())) return false;

    final String text = builder.getTokenText();
    if (StringUtil.isEmpty(text)) return false;
    assert text != null;

    final char firstChar = text.charAt(0);
    return (Character.isLowerCase(firstChar) || !Character.isLetter(firstChar)) &&
           (ParserUtils.lookAhead(builder, GroovyTokenTypes.mIDENT, GroovyTokenTypes.mIDENT) || ParserUtils.lookAhead(builder,
                                                                                                                      GroovyTokenTypes.mIDENT,
                                                                                                                      GroovyTokenTypes.mLPAREN));
  }

  private static IElementType parseDeclarationWithoutGenerics(@NotNull PsiBuilder builder,
                                                              boolean isInClass,
                                                              boolean isInAnnotation,
                                                              @Nullable String typeDefinitionName,
                                                              @NotNull GroovyParser parser,
                                                              boolean modifiersParsed,
                                                              boolean expressionPossible) {
    PsiBuilder.Marker checkMarker = builder.mark(); //point to begin of type or variable

    ReferenceElement.ReferenceElementResult typeResult = TypeSpec.parse(builder, false, expressionPossible);
    if (typeResult == ReferenceElement.ReferenceElementResult.FAIL) { //if type wasn't recognized trying parse VariableDeclaration
      checkMarker.rollbackTo();

      if (isInAnnotation) {
        builder.error(GroovyBundle.message("type.expected"));
      }

      //current token isn't identifier
      return VariableDefinitions.parseDefinitions(builder, isInClass, isInAnnotation, typeDefinitionName, modifiersParsed, true, parser);
    }
    else {  //type was recognized, identifier here
      //starts after type
      IElementType varDeclarationTop = VariableDefinitions.parseDefinitions(builder, isInClass, isInAnnotation, typeDefinitionName,
                                                                            modifiersParsed, false, parser);

      if (varDeclarationTop == GroovyElementTypes.WRONGWAY) {
        if (typeResult == ReferenceElement.ReferenceElementResult.REF_WITH_TYPE_PARAMS) {
          checkMarker.drop();
          return GroovyElementTypes.VARIABLE_DEFINITION_ERROR;
        }

        checkMarker.rollbackTo();

        if (isInAnnotation) {
          builder.error(GroovyBundle.message("type.expected"));
        }

        //starts before "type" identifier, here can't be tuple, because next token is identifier (we are in "type recognized" branch)
        return VariableDefinitions.parseDefinitions(builder, isInClass, isInAnnotation, typeDefinitionName, modifiersParsed, false, parser);
      }
      else {
        checkMarker.drop();
        return varDeclarationTop;
      }
    }
  }

  private static IElementType parseDeclarationWithGenerics(@NotNull PsiBuilder builder,
                                                           boolean isInClass,
                                                           boolean isInAnnotation,
                                                           @Nullable String typeDefinitionName,
                                                           @NotNull GroovyParser parser,
                                                           boolean modifiersParsed,
                                                           boolean expressionPossible) {
    final PsiBuilder.Marker start = builder.mark();

    final IElementType type = tryParseWithGenerics(builder, isInClass, isInAnnotation, typeDefinitionName, parser, modifiersParsed, expressionPossible, true);

    if (type == GroovyElementTypes.WRONGWAY || type == GroovyElementTypes.CONSTRUCTOR_DEFINITION || type ==
                                                                                                    GroovyElementTypes.METHOD_DEFINITION) {
      start.drop();
      return type;
    }

    start.rollbackTo();

    //try to parse variable. So mark type parameters as unexpected
    return tryParseWithGenerics(builder, isInClass, isInAnnotation, typeDefinitionName, parser, modifiersParsed, expressionPossible, false);
  }

  private static IElementType tryParseWithGenerics(@NotNull PsiBuilder builder,
                                                   boolean isInClass,
                                                   boolean isInAnnotation,
                                                   @Nullable String typeDefinitionName,
                                                   @NotNull GroovyParser parser,
                                                   boolean modifiersParsed,
                                                   boolean expressionPossible,
                                                   boolean acceptTypeParameters) {
    if (acceptTypeParameters) {
      TypeParameters.parse(builder);
    }
    else {
      final PsiBuilder.Marker error = builder.mark();
      TypeParameters.parse(builder);
      error.error(GroovyBundle.message("type.parameters.are.unexpected"));
    }

    PsiBuilder.Marker checkMarker = builder.mark(); //point to begin of type or variable

    switch (TypeSpec.parse(builder, false, expressionPossible)) {
      case PATH_REF:
      case REF_WITH_TYPE_PARAMS:
        checkMarker.drop();
        break;
      case FAIL:
        checkMarker.rollbackTo();
        break;
      case IDENTIFIER:
        // declaration name element can be parsed as type element
        IElementType result = VariableDefinitions.parseDefinitions(builder, isInClass, isInAnnotation, typeDefinitionName, modifiersParsed, false, parser);
        if (result == GroovyElementTypes.WRONGWAY) {
          checkMarker.rollbackTo();
        }
        else {
          checkMarker.drop();
          return result;
        }
    }
    return VariableDefinitions.parseDefinitions(builder, isInClass, isInAnnotation, typeDefinitionName, modifiersParsed, false, parser);
  }
}

