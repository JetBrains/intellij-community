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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.blocks;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.grails.lang.gsp.lexer.GspTokenTypesEx;
import org.jetbrains.plugins.grails.lang.gsp.parsing.groovy.GspTemplateStmtParsing;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.Separators;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.parameters.ParameterDeclarationList;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.Statement;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @author ilyas
 */
public class OpenOrClosableBlock implements GroovyElementTypes {


  /**
   * Parses open block as block, not as statement
   *
   * @param builder
   * @return
   */
  public static GroovyElementType parseOpenBlock(PsiBuilder builder) {
    return parseOpenBlockInDifferentContext(builder, false);
  }

  /**
   * Parses open block as a usual statement in role of loop or fork statement body
   *
   * @param builder
   * @return
   */
  public static GroovyElementType parseBlockStatement(PsiBuilder builder) {
    return parseOpenBlockInDifferentContext(builder, true);
  }

  /**
   * Parses only OPEN blocks
   *
   * @param builder
   * @return
   */
  public static GroovyElementType parseOpenBlockInDifferentContext(PsiBuilder builder, boolean isBlockStatement) {
    PsiBuilder.Marker blockStatementMarker = builder.mark();
    PsiBuilder.Marker marker = builder.mark();
    if (!ParserUtils.getToken(builder, mLCURLY)) {
      marker.drop();
      blockStatementMarker.drop();
      return WRONGWAY;
    }
    ParserUtils.getToken(builder, mNLS);
    parseBlockBody(builder);
    while (!builder.eof() &&
        !mRCURLY.equals(builder.getTokenType())) {
      builder.error(GroovyBundle.message("expression.expected"));
      builder.advanceLexer();
    }
    ParserUtils.getToken(builder, mRCURLY, GroovyBundle.message("rcurly.expected"));
    marker.done(OPEN_BLOCK);
    if (isBlockStatement) {
      blockStatementMarker.done(BLOCK_STATEMENT);
    } else {
      blockStatementMarker.drop();
    }
    return OPEN_BLOCK;
  }


  /**
   * Parses CLOSABLE blocks
   *
   * @param builder
   * @return
   */
  public static GroovyElementType parseClosableBlock(PsiBuilder builder) {
    PsiBuilder.Marker marker = builder.mark();
    if (!ParserUtils.getToken(builder, mLCURLY)) {
      marker.drop();
      return WRONGWAY;
    }
    ParserUtils.getToken(builder, mNLS);
    closableBlockParamsOpt(builder);
    parseBlockBody(builder);
    ParserUtils.getToken(builder, mRCURLY, GroovyBundle.message("rcurly.expected"));
    marker.done(CLOSABLE_BLOCK);
    return CLOSABLE_BLOCK;
  }


  private static GroovyElementType closableBlockParamsOpt(PsiBuilder builder) {
    ParameterDeclarationList.parse(builder, mCLOSABLE_BLOCK_OP);
    ParserUtils.getToken(builder, mNLS);
    if (ParserUtils.getToken(builder, mCLOSABLE_BLOCK_OP)) {
      return PARAMETERS_LIST;
    }
    return WRONGWAY;
  }

  public static void parseBlockBody(PsiBuilder builder) {


    GspTemplateStmtParsing.parseGspTemplateStmt(builder);
    if (mSEMI.equals(builder.getTokenType()) || mNLS.equals(builder.getTokenType())) {
      Separators.parse(builder);
    }
    GspTemplateStmtParsing.parseGspTemplateStmt(builder);

    GroovyElementType result = Statement.parse(builder);

    while (!result.equals(WRONGWAY) &&
        (mSEMI.equals(builder.getTokenType()) ||
            mNLS.equals(builder.getTokenType()) ||
            GspTemplateStmtParsing.parseGspTemplateStmt(builder))) {
      Separators.parse(builder);
      while (GspTemplateStmtParsing.parseGspTemplateStmt(builder)) {
        Separators.parse(builder);
      }
      result = Statement.parse(builder);
      if (!GspTokenTypesEx.GSP_GROOVY_SEPARATORS.contains(builder.getTokenType())) {
        cleanAfterError(builder);
      }
    }

    Separators.parse(builder);
    while (GspTemplateStmtParsing.parseGspTemplateStmt(builder)) {
      Separators.parse(builder);
    }

  }

  /**
   * Rolls marker forward after possible errors
   *
   * @param builder
   */
  public static void cleanAfterError(PsiBuilder builder) {
    int i = 0;
    PsiBuilder.Marker em = builder.mark();
    while (!builder.eof() &&
        !(mNLS.equals(builder.getTokenType()) ||
            mRCURLY.equals(builder.getTokenType()) ||
            mSEMI.equals(builder.getTokenType()))
        ) {
      builder.advanceLexer();
      i++;
    }
    if (i > 0) {
      em.error(GroovyBundle.message("separator.or.rcurly.expected"));
    } else {
      em.drop();
    }
  }

}
