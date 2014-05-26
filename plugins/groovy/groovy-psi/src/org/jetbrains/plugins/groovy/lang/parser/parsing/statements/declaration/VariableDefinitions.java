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
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.ThrowClause;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.annotations.AnnotationArguments;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.parameters.ParameterList;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.TupleParse;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.blocks.OpenOrClosableBlock;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.constructor.ConstructorBody;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.AssignmentExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 16.03.2007
 */

public class VariableDefinitions {

  public static IElementType parseDefinitions(@NotNull PsiBuilder builder,
                                              boolean isInClass,
                                              boolean isInAnnotation,
                                              @Nullable String typeDefinitionName,
                                              boolean hasModifiers,
                                              boolean canBeTuple,
                                              @NotNull GroovyParser parser) {
    boolean isLParenth = builder.getTokenType() == GroovyTokenTypes.mLPAREN;

    boolean isStringName = builder.getTokenType() == GroovyTokenTypes.mSTRING_LITERAL || builder.getTokenType() ==
                                                                                         GroovyTokenTypes.mGSTRING_LITERAL;

    if (builder.getTokenType() != GroovyTokenTypes.mIDENT && !isStringName && !isLParenth) {
      builder.error(GroovyBundle.message("indentifier.or.string.or.left.parenth.literal.expected"));
      return GroovyElementTypes.WRONGWAY;
    }

    if (isLParenth && !canBeTuple) {
      builder.error(GroovyBundle.message("indentifier.or.string.or.left.parenth.literal.expected"));
      return GroovyElementTypes.WRONGWAY;
    }

    if (isInAnnotation && isStringName) {
      builder.error(GroovyBundle.message("string.name.unexpected"));
    }

    if (!isLParenth) { //id or string => method name
      PsiBuilder.Marker varMarker = builder.mark();

      final boolean isConstructor = isInClass &&
                                    !isInAnnotation &&
                                    typeDefinitionName != null &&
                                    builder.getTokenType() == GroovyTokenTypes.mIDENT &&
                                    typeDefinitionName.equals(builder.getTokenText());
      builder.advanceLexer();

      if (GroovyTokenTypes.mLPAREN != builder.getTokenType()) {
        varMarker.rollbackTo();
      }
      else {
        varMarker.drop();
        return parseMethod(builder, isInAnnotation, hasModifiers, parser, isConstructor);
      }
    }

    return parseVar(builder, isInClass, hasModifiers, parser, isLParenth);
  }

  private static IElementType parseVar(PsiBuilder builder, boolean isInClass, boolean hasModifiers, GroovyParser parser, boolean LParenth) {
    // a = b, c = d
    PsiBuilder.Marker varAssMarker = builder.mark();

    final IElementType declarator = parseDeclarator(builder, LParenth);

    if (declarator != GroovyElementTypes.WRONGWAY) {
      final boolean wasAssignment = parseAssignment(builder, parser);

      if (declarator == GroovyElementTypes.TUPLE_DECLARATION) {
        varAssMarker.drop();
        if (!wasAssignment && !hasModifiers) {
          builder.error(GroovyBundle.message("assignment.expected"));
          return GroovyElementTypes.WRONGWAY;
        }
      }
      else if (isInClass) { // a = b, c = d
        varAssMarker.done(GroovyElementTypes.FIELD);
      }
      else {
        varAssMarker.done(GroovyElementTypes.VARIABLE);
      }

      while (ParserUtils.getToken(builder, GroovyTokenTypes.mCOMMA)) {
        ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);

        if (GroovyElementTypes.WRONGWAY.equals(parseVariableOrField(builder, isInClass, parser)) && declarator == GroovyTokenTypes.mIDENT) {
          return GroovyElementTypes.VARIABLE_DEFINITION_ERROR; //parse b = d
        }
      }

      if (isInClass && declarator == GroovyElementTypes.TUPLE_DECLARATION) {
        builder.error(GroovyBundle.message("tuple.cant.be.placed.in.class"));
      }
      return GroovyElementTypes.VARIABLE_DEFINITION;
    }
    else {
      varAssMarker.drop();
      builder.error(GroovyBundle.message("identifier.expected"));
      return GroovyElementTypes.WRONGWAY;
    }
  }

  private static IElementType parseMethod(PsiBuilder builder,
                                          boolean isAnnotationMember,
                                          boolean hasModifiers,
                                          GroovyParser parser,
                                          boolean constructor) {
    //if we have no modifiers and current method is not constructor there is something wrong
    if (!hasModifiers && !constructor) {
      builder.error(GroovyBundle.message("method.definition.without.modifier"));
      return GroovyElementTypes.WRONGWAY;
    }

    builder.advanceLexer();

    ParameterList.parse(builder, GroovyTokenTypes.mRPAREN, parser);

    ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
    if (!ParserUtils.getToken(builder, GroovyTokenTypes.mRPAREN)) {
      builder.error(GroovyBundle.message("rparen.expected"));
      ThrowClause.parse(builder);
      return methodType(isAnnotationMember, constructor);
    }

    if (isAnnotationMember && builder.getTokenType() == GroovyTokenTypes.kDEFAULT) {
      ParserUtils.getToken(builder, GroovyTokenTypes.kDEFAULT);
      ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);

      if (!AnnotationArguments.parseAnnotationMemberValueInitializer(builder, parser)) {
        builder.error(GroovyBundle.message("annotation.initializer.expected"));
      }
    }

    if (ParserUtils.lookAhead(builder, GroovyTokenTypes.mNLS, GroovyTokenTypes.kTHROWS) || ParserUtils.lookAhead(builder,
                                                                                                                 GroovyTokenTypes.mNLS,
                                                                                                                 GroovyTokenTypes.mLCURLY)) {
      ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
    }

    if (isAnnotationMember && builder.getTokenType() == GroovyTokenTypes.kTHROWS) {
      builder.error(GroovyBundle.message("throws.clause.is.not.allowed.in.at.interface"));
    }
    ThrowClause.parse(builder);

    if (builder.getTokenType() == GroovyTokenTypes.mLCURLY || ParserUtils.lookAhead(builder, GroovyTokenTypes.mNLS,
                                                                                    GroovyTokenTypes.mLCURLY)) {
      ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
      if (isAnnotationMember) {
        builder.error(GroovyBundle.message("separator.or.rcurly.expected"));
      }
      if (constructor) {
        ConstructorBody.parseConstructorBody(builder, parser);
      }
      else {
        OpenOrClosableBlock.parseOpenBlock(builder, parser);
      }
    }

    return methodType(isAnnotationMember, constructor);
  }

  private static IElementType methodType(boolean isAnnotationMember, final boolean isConstructor) {
    return isAnnotationMember ? GroovyElementTypes.ANNOTATION_METHOD :
           isConstructor ? GroovyElementTypes.CONSTRUCTOR_DEFINITION :
           GroovyElementTypes.METHOD_DEFINITION;
  }

  //a, a = b

  private static IElementType parseVariableOrField(PsiBuilder builder, boolean isInClass, GroovyParser parser) {
    PsiBuilder.Marker varAssMarker = builder.mark();
    if (ParserUtils.getToken(builder, GroovyTokenTypes.mIDENT)) {
      parseAssignment(builder, parser);
      if (isInClass) {
        varAssMarker.done(GroovyElementTypes.FIELD);
        return GroovyElementTypes.FIELD;
      }
      else {
        varAssMarker.done(GroovyElementTypes.VARIABLE);
        return GroovyElementTypes.VARIABLE;
      }
    }
    else {
      varAssMarker.drop();
      builder.error("Identifier expected");
      return GroovyElementTypes.WRONGWAY;
    }
  }

  private static IElementType parseDeclarator(PsiBuilder builder, boolean isTuple) {
    if (isTuple && builder.getTokenType() == GroovyTokenTypes.mLPAREN && TupleParse.parseTupleForVariableDeclaration(builder)) {
      return GroovyElementTypes.TUPLE_DECLARATION;
    }
    if (!isTuple && ParserUtils.getToken(builder, GroovyTokenTypes.mIDENT)) {
      return GroovyTokenTypes.mIDENT;
    }
    return GroovyElementTypes.WRONGWAY;
  }


  private static boolean parseAssignment(PsiBuilder builder, GroovyParser parser) {
    if (ParserUtils.getToken(builder, GroovyTokenTypes.mASSIGN)) {
      PsiBuilder.Marker marker = builder.mark();
      ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);

      if (!AssignmentExpression.parse(builder, parser, true)) {
        marker.rollbackTo();
        builder.error(GroovyBundle.message("expression.expected"));
        return false;
      }
      else {
        marker.drop();
        return true;
      }
    }
    return false;
  }
}
