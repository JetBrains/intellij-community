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
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.ThrowClause;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.annotations.AnnotationArguments;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.parameters.ParameterDeclarationList;
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
 */

public class VariableDefinitions implements GroovyElementTypes {
  public static GroovyElementType parse(PsiBuilder builder, boolean isInClass) {
    return parseDefinitions(builder, isInClass, false, false);
  }

  public static GroovyElementType parseDefinitions(PsiBuilder builder, boolean isInClass, boolean isEnumConstantMember, boolean isAnnotationMember) {
    if (!(builder.getTokenType() == mIDENT || builder.getTokenType() == mSTRING_LITERAL || builder.getTokenType() == mGSTRING_LITERAL)) {
      builder.error(GroovyBundle.message("indentifier.or.string.literal.expected"));
      return WRONGWAY;
    }

    PsiBuilder.Marker varMarker = builder.mark();
    boolean isStringName = builder.getTokenType() == mSTRING_LITERAL || builder.getTokenType() == mGSTRING_LITERAL;

    if (isAnnotationMember && isStringName) {
      builder.error(GroovyBundle.message("string.name.unexpected"));
    }

    //eaten one of these tokens
    boolean eaten = ParserUtils.getToken(builder, mIDENT) || ParserUtils.getToken(builder, mSTRING_LITERAL) || ParserUtils.getToken(builder, mGSTRING_LITERAL);

    if (!eaten) return WRONGWAY;

    if (ParserUtils.getToken(builder, mLPAREN)) {
      GroovyElementType paramDeclList = ParameterDeclarationList.parse(builder, mRPAREN);

//      if (!ParserUtils.getToken(builder, mRPAREN)) {
//        builder.error(GroovyBundle.message("rparen.expected"));
//        varMarker.drop();
//        return WRONGWAY;

      if (isEnumConstantMember && !isStringName) {
        builder.error(GroovyBundle.message("string.name.unexpected"));
      }

      ParserUtils.getToken(builder, mNLS);
      if (!ParserUtils.getToken(builder, mRPAREN)) {
        /*ThrowClause.parse(builder);
        ParserUtils.waitNextRCurly(builder);
        builder.error(GroovyBundle.message("rparen.expected"));*/
        varMarker.rollbackTo();
        return WRONGWAY;
      }

      if (ParserUtils.lookAhead(builder, GroovyTokenTypes.kDEFAULT)) {
        if (isAnnotationMember && !NONE.equals(paramDeclList)) {
          builder.error(GroovyBundle.message("empty.parameter.list.expected"));
        }

        ParserUtils.getToken(builder, GroovyTokenTypes.kDEFAULT);
        ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);

        boolean b = AnnotationArguments.parseAnnotationMemberValueInitializer(builder);
        if (b) {
          varMarker.done(DEFAULT_ANNOTATION_VALUE);
          return DEFAULT_ANNOTATION_MEMBER;
        }
        else {
          varMarker.rollbackTo();
          return WRONGWAY;
        }
      }

      ThrowClause.parse(builder);

      GroovyElementType openBlock = null;
      if (builder.getTokenType() == mLCURLY || ParserUtils.lookAhead(builder, mNLS, mLCURLY)) {
        ParserUtils.getToken(builder, mNLS);
        openBlock = OpenOrClosableBlock.parseOpenBlock(builder);
      }

//      if (isAnnotationMember && !NONE.equals(paramDeclList) && OPEN_BLOCK.equals(openBlock)) {
//        builder.error(GroovyBundle.message("empty.parameter.list.expected"));
//      }

      varMarker.drop();
      return METHOD_DEFINITION;
    } else

    {
      varMarker.rollbackTo();

      // a = b, c = d
      PsiBuilder.Marker varAssMarker = builder.mark();
      if (ParserUtils.getToken(builder, mIDENT)) {

        if (parseAssignment(builder)) { // a = b, c = d
          if (isInClass) {
            varAssMarker.done(FIELD);
          } else {
            varAssMarker.done(VARIABLE);
          }

          while (ParserUtils.getToken(builder, mCOMMA)) {
            ParserUtils.getToken(builder, mNLS);

            if (WRONGWAY.equals(parseVariableDeclarator(builder, isInClass)))
              return VARIABLE_DEFINITION_ERROR; //parse b = d
          }
          return VARIABLE_DEFINITION;
        } else {
          if (isInClass) {
            varAssMarker.done(FIELD);
          } else {
            varAssMarker.done(VARIABLE);
          }
//          varAssMarker.drop();
          boolean isManyDef = false;
          while (ParserUtils.getToken(builder, mCOMMA)) {// a, b = d, c = d
            ParserUtils.getToken(builder, mNLS);

            if (WRONGWAY.equals(parseVariableDeclarator(builder, isInClass))) return VARIABLE_DEFINITION_ERROR;
            isManyDef = true;
          }

          return VARIABLE_DEFINITION;
        }
      } else {
        varAssMarker.drop();
        builder.error(GroovyBundle.message("identifier.expected"));
        return VARIABLE_DEFINITION_ERROR;
      }

    }
  }

  //a, a = b
  private static GroovyElementType parseVariableDeclarator(PsiBuilder builder, boolean isInClass) {
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

  private static boolean parseAssignment(PsiBuilder builder) {
    if (ParserUtils.getToken(builder, mASSIGN)) {
      ParserUtils.getToken(builder, mNLS);

      if (WRONGWAY.equals(AssignmentExpression.parse(builder))) {
        builder.error(GroovyBundle.message("expression.expected"));
        return false;
      } else {
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
