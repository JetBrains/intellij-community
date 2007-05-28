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
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.identifier.UpperCaseIdent;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.modifiers.Modifiers;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.auxilary.BalancedTokens;
import org.jetbrains.plugins.groovy.lang.parser.parsing.types.BuiltInType;
import org.jetbrains.plugins.groovy.lang.parser.parsing.types.QualifiedTypeName;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 14.03.2007
 */

/**
 * DeclarationStart ::= "def"
 * | modifier
 * | @IDENT
 * | (upperCaseIdent | builtInType | QulifiedTypeName)  {LBRACK balacedTokens RBRACK} IDENT
 */

public class DeclarationStart implements GroovyElementTypes {
  /*
   * @deprecated
   */

  public static boolean parse(PsiBuilder builder) {
    PsiBuilder.Marker declStartMarker = builder.mark();

    if (!WRONGWAY.equals(Declaration.parse(builder, false, false))) {
      declStartMarker.rollbackTo();
      return true;
    } else {
      declStartMarker.rollbackTo();
      return false;
    }
  }

  public static boolean parseDeclarationStart(PsiBuilder builder) {
    PsiBuilder.Marker declStartMarker = builder.mark();
    IElementType elementType;

    //def
    if (ParserUtils.getToken(builder, kDEF)) {
      if (parseNextTokenInDeclaration(builder)) {
        declStartMarker.rollbackTo();
        return true;
      } else {
        declStartMarker.rollbackTo();
        return false;
      }
    }

    //Modifiers
    elementType = Modifiers.parse(builder);
    if (!WRONGWAY.equals(elementType)) {
      if (parseNextTokenInDeclaration(builder)) {
        declStartMarker.rollbackTo();
        return true;
      } else {
        declStartMarker.rollbackTo();
        return false;
      }
    }

    //@IDENT
    if (ParserUtils.getToken(builder, mAT)) {
      declStartMarker.rollbackTo();
      return builder.getTokenType() == mIDENT;
    }

    // (upperCaseIdent | builtInType | QulifiedTypeName)  {LBRACK balacedTokens RBRACK} IDENT
    if (UpperCaseIdent.parse(builder) || !WRONGWAY.equals(BuiltInType.parse(builder)) || !WRONGWAY.equals(QualifiedTypeName.parse(builder))) {

      IElementType balancedTokens;

      do {
        balancedTokens = parseBalancedTokensInBrackets(builder);
      } while (!NONE.equals(balancedTokens) && !WRONGWAY.equals(balancedTokens));

      //IDENT
      if (ParserUtils.getToken(builder, mIDENT) && !ParserUtils.getToken(builder, mDOT)) {
        declStartMarker.rollbackTo();
        return true;
      } else {
        declStartMarker.rollbackTo();
        return false;
      }

    } else {
      declStartMarker.rollbackTo();
      return false;
    }
  }

  //todo: check it
  private static boolean parseNextTokenInDeclaration(PsiBuilder builder) {
    return builder.getTokenType() == mIDENT ||
            TokenSets.BUILT_IN_TYPE.contains(builder.getTokenType()) ||
            TokenSets.MODIFIERS.contains(builder.getTokenType()) ||
        builder.getTokenType() == kDEF ||
        builder.getTokenType() == mAT ||
        builder.getTokenType() == mASSIGN ||
        builder.getTokenType() == mGSTRING_LITERAL ||
        builder.getTokenType() == mSTRING_LITERAL;
  }

  private static IElementType parseBalancedTokensInBrackets(PsiBuilder builder) {
    PsiBuilder.Marker btm = builder.mark();

    if (!ParserUtils.getToken(builder, mLBRACK, GroovyBundle.message("lbrack.expected"))) {
      btm.rollbackTo();
      return NONE;
    }

    if (WRONGWAY.equals(BalancedTokens.parse(builder))) {
      btm.rollbackTo();
      return NONE;
    }

    if (!ParserUtils.getToken(builder, mRBRACK, GroovyBundle.message("rbrack.expected"))) {
      btm.rollbackTo();
      return NONE;
    }

    btm.done(BALANCED_TOKENS);

    return BALANCED_TOKENS;
  }
}