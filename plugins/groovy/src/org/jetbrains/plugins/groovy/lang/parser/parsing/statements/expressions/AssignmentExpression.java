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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;


/**
 * @author ilyas
 */
public class AssignmentExpression implements GroovyElementTypes {

  private static final TokenSet ASSIGNMENTS = TokenSet.create(
          mASSIGN,
          mPLUS_ASSIGN,
          mMINUS_ASSIGN,
          mSTAR_ASSIGN,
          mDIV_ASSIGN,
          mMOD_ASSIGN,
          mSL_ASSIGN,
          mBAND_ASSIGN,
          mBOR_ASSIGN,
          mBXOR_ASSIGN,                                       
          mSTAR_STAR_ASSIGN,
          mSR_ASSIGN,
          mBSR_ASSIGN
  );

  public static GroovyElementType parse(PsiBuilder builder) {
    Marker marker = builder.mark();
    GroovyElementType result = ConditionalExpression.parse(builder);
    if (result != WRONGWAY && ParserUtils.getToken(builder, ASSIGNMENTS)) {
      ParserUtils.getToken(builder, mNLS);
      result = parse(builder);
      if (result.equals(WRONGWAY)) {
        builder.error(GroovyBundle.message("expression.expected"));
      }
      marker.done(ASSIGNMENT_EXPRESSION);
      return ASSIGNMENT_EXPRESSION;
    } else {
      marker.drop();
    }

    return result;
  }


}
