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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.AssignmentExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.StrictContextExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @author ilyas
 */
public class SwitchStatement implements GroovyElementTypes {

  public static boolean parse(PsiBuilder builder, GroovyParser parser) {
    PsiBuilder.Marker marker = builder.mark();
    ParserUtils.getToken(builder, kSWITCH);

    if (!ParserUtils.getToken(builder, mLPAREN, GroovyBundle.message("lparen.expected"))) {
      marker.done(SWITCH_STATEMENT);
      return true;
    }
    if (!StrictContextExpression.parse(builder, parser)) {
      builder.error(GroovyBundle.message("expression.expected"));
    }
    ParserUtils.getToken(builder, mNLS);

    if (!ParserUtils.getToken(builder, mRPAREN, GroovyBundle.message("rparen.expected"))) {
      while (!builder.eof() && !mNLS.equals(builder.getTokenType()) && !mRPAREN.equals(builder.getTokenType())) {
        builder.error(GroovyBundle.message("rparen.expected"));
        builder.advanceLexer();
      }
      if (!ParserUtils.getToken(builder, mRPAREN)) {
        marker.done(SWITCH_STATEMENT);
        return true;
      }
    }
    PsiBuilder.Marker warn = builder.mark();
    ParserUtils.getToken(builder, mNLS);

    if (!mLCURLY.equals(builder.getTokenType())) {
      warn.rollbackTo();
      builder.error(GroovyBundle.message("case.block.expected"));
      marker.done(SWITCH_STATEMENT);
      return true;
    }
    warn.drop();
    parseCaseBlock(builder, parser);
    marker.done(SWITCH_STATEMENT);
    return true;

  }

  private static void parseCaseBlock(PsiBuilder builder, GroovyParser parser) {
    ParserUtils.getToken(builder, mLCURLY);
    ParserUtils.getToken(builder, mNLS);

    while (true) {
      if (ParserUtils.getToken(builder, mRCURLY)) {
        return;
      }

      if (builder.getTokenType() != kCASE && builder.getTokenType() != kDEFAULT) {
        builder.error("case, default or } expected");
        while (true) {
          builder.advanceLexer();
          if (builder.eof() || ParserUtils.getToken(builder, mRCURLY)) {
            return;
          }
          if (builder.getTokenType() == kCASE || builder.getTokenType() == kDEFAULT) {
            break;
          }
        }
      }

      PsiBuilder.Marker sectionMarker = builder.mark();
      parseCaseLabel(builder, parser);
      ParserUtils.getToken(builder, mNLS);
      if (builder.getTokenType() == mRCURLY) {
        builder.error(GroovyBundle.message("expression.expected"));
      } else {
        parser.parseSwitchCaseList(builder);
      }
      sectionMarker.done(CASE_SECTION);
      ParserUtils.getToken(builder, mNLS);
    }
  }

  /**
   * Parses one or more sequential 'case' or 'default' labels
   */
  public static boolean parseCaseLabel(PsiBuilder builder, GroovyParser parser) {
    IElementType elem = builder.getTokenType();
    if (elem != kCASE && elem != kDEFAULT) {
      return false;
    }

    PsiBuilder.Marker label = builder.mark();
    builder.advanceLexer();
    if (kCASE.equals(elem)) {
      AssignmentExpression.parse(builder, parser, true);
    }
    ParserUtils.getToken(builder, mCOLON, GroovyBundle.message("colon.expected"));
    label.done(CASE_LABEL);
    PsiBuilder.Marker beforeNls = builder.mark();
    ParserUtils.getToken(builder, mNLS);
    if (parseCaseLabel(builder, parser)) {
      beforeNls.drop();
    } else {
      beforeNls.rollbackTo();
    }
    return true;
  }

}
