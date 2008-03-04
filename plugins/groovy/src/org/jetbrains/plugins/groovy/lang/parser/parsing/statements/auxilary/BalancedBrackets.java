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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.auxilary;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.Pairs;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 16.03.2007
 */
public class BalancedBrackets implements GroovyElementTypes {
  public static boolean parse(PsiBuilder builder) {
    PsiBuilder.Marker bbm = builder.mark();

    IElementType myBracket = null;

    if (ParserUtils.getToken(builder, mLPAREN)) {
      myBracket = mLPAREN;
    }

    if (ParserUtils.getToken(builder, mLCURLY)) {
      myBracket = mLCURLY;
    }

    if (ParserUtils.getToken(builder, mGSTRING_SINGLE_BEGIN)) {
      myBracket = mGSTRING_SINGLE_BEGIN;
    }

    if (myBracket == null) {
      bbm.rollbackTo();
      builder.error(GroovyBundle.message("lbrack.or.lparen.or.lcurly.or.string_ctor_start.expected"));
      return false;
    }

    if (!BalancedTokens.parse(builder)) {
      bbm.rollbackTo();
      return false;
    }

    if (ParserUtils.getToken(builder, mRPAREN) && !mRPAREN.equals(Pairs.pairElementsMap.get(myBracket))
            || ParserUtils.getToken(builder, mRBRACK) && !mRBRACK.equals(Pairs.pairElementsMap.get(myBracket))
            || ParserUtils.getToken(builder, mRCURLY) && !mRCURLY.equals(Pairs.pairElementsMap.get(myBracket))
            || ParserUtils.getToken(builder, mGSTRING_SINGLE_END) && !mGSTRING_SINGLE_END.equals(Pairs.pairElementsMap.get(myBracket))) {
      bbm.rollbackTo();
      return false;
    } else {
      bbm.done(BALANCED_BRACKETS);
      return true;
    }
  }
}
