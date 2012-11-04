/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.ThrowClause;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.annotations.AnnotationArguments;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.parameters.ParameterList;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.TupleParse;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.blocks.OpenOrClosableBlock;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.AssignmentExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 16.03.2007
 */

/*
 * variableDefinitions ::=
 *      variableDeclarator ( COMMA nls variableDeclarator )*
 *    |	(	IDENT |	STRING_LITERAL )
 *      LPAREN parameterDeclarationList RPAREN
 *      (throwsClause | )
 *      (	( nlsWarn openBlock ) | )
 * | (ID {, ID}) = expr
 */

public class VariableDefinitions implements GroovyElementTypes {

  public static IElementType parseDefinitions(PsiBuilder builder,
                                              boolean isInClass,
                                              boolean isAnnotationMember,
                                              boolean mustBeMethod,
                                              boolean hasModifiers,
                                              boolean canBeTuple,
                                              GroovyParser parser) {
    boolean isLParen = builder.getTokenType() == mLPAREN;

    boolean isStringName = builder.getTokenType() == mSTRING_LITERAL || builder.getTokenType() == mGSTRING_LITERAL;

    if (builder.getTokenType() != mIDENT && !isStringName && !isLParen) {
      builder.error(GroovyBundle.message("indentifier.or.string.or.left.parenth.literal.expected"));
      return WRONGWAY;
    }

    if (isLParen && !canBeTuple) {
      builder.error(GroovyBundle.message("indentifier.or.string.or.left.parenth.literal.expected"));
      return WRONGWAY;
    }

    if (isLParen && mustBeMethod) {
      return WRONGWAY;
    }

    if (isAnnotationMember && isStringName) {
      builder.error(GroovyBundle.message("string.name.unexpected"));
    }

    if (!isLParen) { //id or string => method name
      PsiBuilder.Marker varMarker = builder.mark();
      builder.advanceLexer();

      if (mLPAREN != builder.getTokenType()) {
        if (mustBeMethod) {
          varMarker.drop();
          return WRONGWAY;
        }
        varMarker.rollbackTo();
      }
      else {
        varMarker.drop();

        if (!hasModifiers) {
          builder.error(GroovyBundle.message("method.definition.without.modifier"));
          return WRONGWAY;
        }

        builder.advanceLexer();

        ParameterList.parse(builder, mRPAREN, parser);

        ParserUtils.getToken(builder, mNLS);
        if (!ParserUtils.getToken(builder, mRPAREN)) {
          builder.error(GroovyBundle.message("rparen.expected"));
          ThrowClause.parse(builder);
          return isAnnotationMember ? ANNOTATION_METHOD : METHOD_DEFINITION;
        }

        if (isAnnotationMember && builder.getTokenType() == kDEFAULT) {
          PsiBuilder.Marker defaultValueMarker = builder.mark();
          ParserUtils.getToken(builder, kDEFAULT);
          ParserUtils.getToken(builder, mNLS);

          if (!AnnotationArguments.parseAnnotationMemberValueInitializer(builder, parser)) {
            builder.error(GroovyBundle.message("annotation.initializer.expected"));
          }

          defaultValueMarker.done(DEFAULT_ANNOTATION_VALUE);
        }

        if (ParserUtils.lookAhead(builder, mNLS, kTHROWS) || ParserUtils.lookAhead(builder, mNLS, mLCURLY)) {
          ParserUtils.getToken(builder, mNLS);
        }

        if (isAnnotationMember && builder.getTokenType() == kTHROWS) {
          builder.error(GroovyBundle.message("throws.clause.is.not.allowed.in.at.interface"));
        }
        ThrowClause.parse(builder);

        if (builder.getTokenType() == mLCURLY || ParserUtils.lookAhead(builder, mNLS, mLCURLY)) {
          ParserUtils.getToken(builder, mNLS);
          if (isAnnotationMember) {
            builder.error(GroovyBundle.message("separator.or.rcurly.expected"));
          }
          OpenOrClosableBlock.parseOpenBlock(builder, parser);
        }

        return isAnnotationMember ? ANNOTATION_METHOD : METHOD_DEFINITION;
      }
    }

    // a = b, c = d
    PsiBuilder.Marker varAssMarker = builder.mark();

    final IElementType declarator = parseDeclarator(builder, isLParen);

    if (declarator != WRONGWAY) {
      final boolean wasAssingment = parseAssignment(builder, parser);

      if (declarator == TUPLE_DECLARATION) {
        varAssMarker.drop();
        if (!wasAssingment && !hasModifiers) {
          builder.error(GroovyBundle.message("assignment.expected"));
          return WRONGWAY;
        }
      }
      else if (isInClass) { // a = b, c = d
        varAssMarker.done(FIELD);
      }
      else {
        varAssMarker.done(VARIABLE);
      }

      while (ParserUtils.getToken(builder, mCOMMA)) {
        ParserUtils.getToken(builder, mNLS);

        if (WRONGWAY.equals(parseVariableOrField(builder, isInClass, parser)) && declarator == mIDENT) {
          return VARIABLE_DEFINITION_ERROR; //parse b = d
        }
      }

      if (isInClass && declarator == TUPLE_DECLARATION) {
        builder.error(GroovyBundle.message("tuple.cant.be.placed.in.class"));
      }
      return VARIABLE_DEFINITION;
    }
    else {
      varAssMarker.drop();
      builder.error(GroovyBundle.message("identifier.expected"));
      return WRONGWAY;
    }
  }

  //a, a = b

  private static IElementType parseVariableOrField(PsiBuilder builder, boolean isInClass, GroovyParser parser) {
    PsiBuilder.Marker varAssMarker = builder.mark();
    if (ParserUtils.getToken(builder, mIDENT)) {
      parseAssignment(builder, parser);
      if (isInClass) {
        varAssMarker.done(FIELD);
        return FIELD;
      }
      else {
        varAssMarker.done(VARIABLE);
        return VARIABLE;
      }
    }
    else {
      varAssMarker.drop();
      builder.error("Identifier expected");
      return WRONGWAY;
    }
  }

  private static IElementType parseDeclarator(PsiBuilder builder, boolean isTuple) {
    if (isTuple && builder.getTokenType() == mLPAREN && TupleParse.parseTupleForVariableDeclaration(builder)) {
      return TUPLE_DECLARATION;
    }
    if (!isTuple && ParserUtils.getToken(builder, mIDENT)) {
      return mIDENT;
    }
    return WRONGWAY;
  }


  private static boolean parseAssignment(PsiBuilder builder, GroovyParser parser) {
    if (ParserUtils.getToken(builder, mASSIGN)) {
      PsiBuilder.Marker marker = builder.mark();
      ParserUtils.getToken(builder, mNLS);

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
