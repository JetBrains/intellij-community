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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.declaration;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.ThrowClause;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.annotations.AnnotationArguments;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.parameters.ParameterList;
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
  public static IElementType parse(PsiBuilder builder, boolean isInClass, boolean hasModifiers) {
    return parseDefinitions(builder, isInClass, false, false, false, hasModifiers, true);
  }

  public static IElementType parse(PsiBuilder builder, boolean isInClass, boolean hasModifiers, boolean canBeTuple) {
    return parseDefinitions(builder, isInClass, false, false, false, hasModifiers, canBeTuple);
  }

  public static IElementType parseDefinitions(PsiBuilder builder,
                                              boolean isInClass,
                                              boolean isEnumConstantMember,
                                              boolean isAnnotationMember,
                                              boolean mustBeMethod,
                                              boolean hasModifiers,
                                              boolean canBeTuple) {
    if (builder.getTokenType() == mLPAREN && !canBeTuple) {
      builder.error(GroovyBundle.message("indentifier.or.string.or.left.parenth.literal.expected"));
      return WRONGWAY;
    }

    if (!(builder.getTokenType() == mIDENT ||
          builder.getTokenType() == mSTRING_LITERAL ||
          builder.getTokenType() == mGSTRING_LITERAL ||
          builder.getTokenType() == mLPAREN)) {
      builder.error(GroovyBundle.message("indentifier.or.string.or.left.parenth.literal.expected"));
      return WRONGWAY;
    }

    PsiBuilder.Marker varMarker = builder.mark();
    boolean isStringName = builder.getTokenType() == mSTRING_LITERAL || builder.getTokenType() == mGSTRING_LITERAL;

    if (isAnnotationMember && isStringName) {
      builder.error(GroovyBundle.message("string.name.unexpected"));
    }

    //eaten one of these tokens
    boolean isTuple = builder.getTokenType() == mLPAREN;
    boolean eaten = ParserUtils.getToken(builder, mIDENT) ||
                    ParserUtils.getToken(builder, mSTRING_LITERAL) ||
                    ParserUtils.getToken(builder, mGSTRING_LITERAL) ||
                    ParserUtils.getToken(builder, mLPAREN);

    if (!eaten && !isTuple) return WRONGWAY;

    if (mustBeMethod && mLPAREN != builder.getTokenType()) {
      varMarker.drop();
      return WRONGWAY;
    }

    if (!hasModifiers && mLPAREN == builder.getTokenType()) {
      builder.error(GroovyBundle.message("method.definition.without.modifier"));
      varMarker.drop();
      return WRONGWAY;
    }

    if (ParserUtils.getToken(builder, mLPAREN)) {
      ParameterList.parse(builder, mRPAREN);

      if (isEnumConstantMember && !isStringName) {
        builder.error(GroovyBundle.message("string.name.unexpected"));
      }

      ParserUtils.getToken(builder, mNLS);
      if (!ParserUtils.getToken(builder, mRPAREN)) {
        builder.error(GroovyBundle.message("rparen.expected"));
        varMarker.drop();
        ThrowClause.parse(builder);
        return METHOD_DEFINITION;
      }

      varMarker.drop();
      if (builder.getTokenType() == kDEFAULT) {
        PsiBuilder.Marker defaultValueMarker = builder.mark();
        ParserUtils.getToken(builder, GroovyTokenTypes.kDEFAULT);
        ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);

        if (!AnnotationArguments.parseAnnotationMemberValueInitializer(builder)) {
          builder.error(GroovyBundle.message("annotation.initializer.expected"));
        }

        defaultValueMarker.done(DEFAULT_ANNOTATION_VALUE);
        return ANNOTATION_METHOD;
      }
      if (ParserUtils.lookAhead(builder, mNLS, kTHROWS) || ParserUtils.lookAhead(builder, mNLS, mLCURLY)) {
        ParserUtils.getToken(builder, mNLS);
      }

      ThrowClause.parse(builder);

      if (builder.getTokenType() == mLCURLY || ParserUtils.lookAhead(builder, mNLS, mLCURLY)) {
        ParserUtils.getToken(builder, mNLS);
        OpenOrClosableBlock.parseOpenBlock(builder);
      }

//      if (isAnnotationMember && !NONE.equals(paramDeclList) && OPEN_BLOCK.equals(openBlock)) {
//        builder.error(GroovyBundle.message("empty.parameter.list.expected"));
//      }

      return METHOD_DEFINITION;
    } else {
      varMarker.rollbackTo();

      // a = b, c = d
      PsiBuilder.Marker varAssMarker = builder.mark();

      final IElementType declarator = parseDeclarator(builder, isInClass, isTuple, hasModifiers);

      if (declarator != WRONGWAY) {
        final boolean wasAssingment = parseAssignment(builder);

        if (declarator == TUPLE) {
          varAssMarker.drop();
          if (!wasAssingment && !hasModifiers) {
            builder.error(GroovyBundle.message("assignment.expected"));
          }
        } else if (declarator == mIDENT) { //if (isAssignment) { // a = b, c = d
          if (isInClass) {
            varAssMarker.done(FIELD);
          } else {
            varAssMarker.done(VARIABLE);
          }
        }

        while (ParserUtils.getToken(builder, mCOMMA)) {
          ParserUtils.getToken(builder, mNLS);

          if (WRONGWAY.equals(parseVariableOrField(builder, isInClass)) && declarator == mIDENT) {
            return VARIABLE_DEFINITION_ERROR; //parse b = d
          }
        }

        if (isInClass && declarator == TUPLE) {
          builder.error(GroovyBundle.message("tuple.cant.be.placed.in.class"));
        }
        return declarator == TUPLE ? MULTIPLE_VARIABLE_DEFINITION : VARIABLE_DEFINITION;
      } else {
        varAssMarker.drop();
        builder.error(GroovyBundle.message("identifier.expected"));
        return WRONGWAY;
      }
    }
  }

  //a, a = b

  private static IElementType parseVariableOrField(PsiBuilder builder, boolean isInClass) {
    PsiBuilder.Marker varAssMarker = builder.mark();
    if (ParserUtils.getToken(builder, mIDENT)) {
      parseAssignment(builder);
      if (isInClass) {
        varAssMarker.done(FIELD);
        return FIELD;
      } else {
        varAssMarker.done(VARIABLE);
        return VARIABLE;
      }
    } else {
      varAssMarker.drop();
      return WRONGWAY;
    }
  }

  private static IElementType parseDeclarator(PsiBuilder builder, boolean inClass, boolean isTuple, boolean hasModifiers) {
    if (!isTuple) {
      if (builder.getTokenType() == mIDENT) {
        ParserUtils.getToken(builder, mIDENT);
        return mIDENT;
      } else {
        return WRONGWAY;
      }
    } else if (ParserUtils.getToken(builder, mLPAREN)) {
      PsiBuilder.Marker tupleMarker = builder.mark();

      final PsiBuilder.Marker firstVarMarker = builder.mark();
      if (!ParserUtils.getToken(builder, mIDENT)) {
        tupleMarker.drop();
        firstVarMarker.drop();
        return WRONGWAY;
      } else {
        firstVarMarker.done(VARIABLE);
      }

      while (!builder.eof() && builder.getTokenType() != mRPAREN && builder.getTokenType() != mASSIGN) {
        if (!ParserUtils.getToken(builder, mCOMMA)) {
          builder.error(GroovyBundle.message("comma.expected"));

          if (!hasModifiers) {
            tupleMarker.drop();
            return WRONGWAY;
          }

        } else {
          PsiBuilder.Marker varMarker = builder.mark();
          if (!ParserUtils.getToken(builder, mIDENT)) {
            builder.error(GroovyBundle.message("identifier.expected"));
            varMarker.drop();
          } else {
            varMarker.done(VARIABLE);
          }                             
        }
      }

      if (ParserUtils.getToken(builder, mRPAREN)) {
        if (builder.getTokenType() != mASSIGN) {
          if (!hasModifiers) {
            tupleMarker.drop();
            return WRONGWAY;
          }
        }
        tupleMarker.done(TUPLE);
        return TUPLE;
      }

      if (builder.getTokenType() == mASSIGN) {
        builder.error("rparen.expected");
        tupleMarker.done(TUPLE_ERROR);
        return TUPLE_ERROR;
      }

      tupleMarker.drop();
      return WRONGWAY;
    } else {
      return WRONGWAY;
    }
  }

  private static boolean parseAssignment(PsiBuilder builder) {
    if (ParserUtils.getToken(builder, mASSIGN)) {
      PsiBuilder.Marker marker = builder.mark();
      ParserUtils.getToken(builder, mNLS);

      if (!AssignmentExpression.parse(builder)) {
        marker.rollbackTo();
        builder.error(GroovyBundle.message("expression.expected"));
        return false;
      } else {
        marker.drop();
        return true;
      }
    }
    return false;
  }

//  private static boolean parseAnnotationMemberValueInitializer(PsiBuilder builder) {
//    return !WRONGWAY.equals(Annotation.parse(builder)) || !WRONGWAY.equals(ConditionalExpression.parse(builder));
//  }

//  public static GroovyElementType parseAnnotationMember(PsiBuilder builder) {
//    return parseDefinitions(builder, false, false, true);
//  }
}
