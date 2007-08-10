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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.Separators;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.blocks.OpenOrClosableBlock;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.AssignmentExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.StrictContextExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.grails.lang.gsp.parsing.groovy.GspTemplateStmtParsing;
import org.jetbrains.plugins.grails.lang.gsp.lexer.GspTokenTypesEx;

/**
 * @author ilyas
 */
public class SwitchStatement implements GroovyElementTypes {

  public static GroovyElementType parse(PsiBuilder builder) {
    PsiBuilder.Marker marker = builder.mark();
    ParserUtils.getToken(builder, kSWITCH);

    if (!ParserUtils.getToken(builder, mLPAREN, GroovyBundle.message("lparen.expected"))) {
      marker.done(SWITCH_STATEMENT);
      return SWITCH_STATEMENT;
    }
    if (StrictContextExpression.parse(builder).equals(WRONGWAY)) {
      builder.error(GroovyBundle.message("expression.expected"));
      marker.done(SWITCH_STATEMENT);
      return SWITCH_STATEMENT;
    }

    ParserUtils.getToken(builder, mNLS);

    if (!ParserUtils.getToken(builder, mRPAREN, GroovyBundle.message("rparen.expected"))) {
      while (!builder.eof() && !mNLS.equals(builder.getTokenType()) && !mRPAREN.equals(builder.getTokenType())) {
        builder.advanceLexer();
      }
      if (!ParserUtils.getToken(builder, mRPAREN)) {
        marker.done(SWITCH_STATEMENT);
        return SWITCH_STATEMENT;
      }
    }
    PsiBuilder.Marker warn = builder.mark();
    ParserUtils.getToken(builder, mNLS);

    if (!mLCURLY.equals(builder.getTokenType())) {
      warn.rollbackTo();
      builder.error(GroovyBundle.message("case.block.expected"));
      marker.done(SWITCH_STATEMENT);
      return SWITCH_STATEMENT;
    }
    warn.drop();
    parseCaseBlock(builder);
    marker.done(SWITCH_STATEMENT);
    return SWITCH_STATEMENT;

  }

  /**
   * Parses cases block
   *
   * @param builder
   */
  private static void parseCaseBlock(PsiBuilder builder) {
    ParserUtils.getToken(builder, mLCURLY);
    ParserUtils.getToken(builder, mNLS);
    if (ParserUtils.getToken(builder, mRCURLY)) {
      return;
    }
    if (!kCASE.equals(builder.getTokenType()) &&
        !kDEFAULT.equals(builder.getTokenType())) {
      builder.error(GroovyBundle.message("case.expected"));
      while (!builder.eof() &&
          !(kCASE.equals(builder.getTokenType()) ||
              kDEFAULT.equals(builder.getTokenType()) ||
              mRCURLY.equals(builder.getTokenType()))) {
        builder.error(GroovyBundle.message("case.expected"));
        builder.advanceLexer();
      }
    }

    while (kCASE.equals(builder.getTokenType()) ||
        kDEFAULT.equals(builder.getTokenType())) {
      PsiBuilder.Marker sectionMarker = builder.mark();
      parseCaseLabel(builder);
      if (builder.getTokenType() == mRCURLY ||
          ParserUtils.lookAhead(builder, mNLS, mRCURLY)) {
        builder.error(GroovyBundle.message("expression.expected"));
      } else {
        parseCaseList(builder);
      }
      sectionMarker.done(CASE_SECTION);
    }
    ParserUtils.getToken(builder, mRCURLY, GroovyBundle.message("rcurly.expected"));
  }

  /**
   * Parses one or more sequential 'case' or 'default' labels
   *
   * @param builder
   */
  public static void parseCaseLabel(PsiBuilder builder) {
    PsiBuilder.Marker label = builder.mark();
    IElementType elem = builder.getTokenType();
    ParserUtils.getToken(builder, TokenSet.create(kCASE, kDEFAULT));

    if (kCASE.equals(elem)) {
      if (WRONGWAY.equals(AssignmentExpression.parse(builder))) {
        label.done(CASE_LABEL);
        builder.error(GroovyBundle.message("expression.expected"));
        return;
      }
    }
    ParserUtils.getToken(builder, mCOLON, GroovyBundle.message("colon.expected"));
    label.done(CASE_LABEL);
    ParserUtils.getToken(builder, mNLS);
    if (builder.getTokenType() == kCASE ||
        builder.getTokenType() == kDEFAULT) {
      parseCaseLabel(builder);
    }
  }

  /**
   * Parses list of statements after case label(s)
   *
   * @param builder
   */
  private static void parseCaseList(PsiBuilder builder) {

    if (kCASE.equals(builder.getTokenType()) ||
        kDEFAULT.equals(builder.getTokenType()) ||
        mRCURLY.equals(builder.getTokenType())) {
      return;
    }
    GroovyElementType result = Statement.parse(builder);
    if (result.equals(WRONGWAY) && !GspTemplateStmtParsing.parseGspTemplateStmt(builder)) {
      builder.error(GroovyBundle.message("wrong.statement"));
      return;
    }

    while (GspTemplateStmtParsing.parseGspTemplateStmt(builder)) {
      if (mSEMI.equals(builder.getTokenType()) || mNLS.equals(builder.getTokenType())) {
        Separators.parse(builder);
      }
    }
    if (mSEMI.equals(builder.getTokenType()) || mNLS.equals(builder.getTokenType())) {
      Separators.parse(builder);
    }

    if (kCASE.equals(builder.getTokenType()) ||
        kDEFAULT.equals(builder.getTokenType()) ||
        mRCURLY.equals(builder.getTokenType())) {
      return;
    }
    result = Statement.parse(builder);
    while (!result.equals(WRONGWAY) && (mSEMI.equals(builder.getTokenType()) || mNLS.equals(builder.getTokenType())) ||
        GspTemplateStmtParsing.parseGspTemplateStmt(builder)) {

      if (mSEMI.equals(builder.getTokenType()) || mNLS.equals(builder.getTokenType())) {
        Separators.parse(builder);
      }
      while (GspTemplateStmtParsing.parseGspTemplateStmt(builder)) {
        if (mSEMI.equals(builder.getTokenType()) || mNLS.equals(builder.getTokenType())) {
          Separators.parse(builder);
        }
      }
      if (mSEMI.equals(builder.getTokenType()) || mNLS.equals(builder.getTokenType())) {
        Separators.parse(builder);
      }

      if (kCASE.equals(builder.getTokenType()) ||
          kDEFAULT.equals(builder.getTokenType()) ||
          mRCURLY.equals(builder.getTokenType())) {
        break;
      }

      result = Statement.parse(builder);
      if (!GspTokenTypesEx.GSP_GROOVY_SEPARATORS.contains(builder.getTokenType())) {
        OpenOrClosableBlock.cleanAfterError(builder);
      }
    }
    Separators.parse(builder);
  }

}
