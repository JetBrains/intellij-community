/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.highlighter;

import com.intellij.ide.PowerSaveMode;
import com.intellij.lexer.LayeredLexer;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.StringLiteralLexer;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.StringEscapesTokenTypes;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyLexer;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;

import java.util.HashMap;
import java.util.Map;

import static org.jetbrains.plugins.groovy.lang.lexer.TokenSets.*;

/**
 * @author ilyas
 */
public class GroovySyntaxHighlighter extends SyntaxHighlighterBase implements GroovyTokenTypes {

  private static final Map<IElementType, TextAttributesKey> ATTRIBUTES = new HashMap<IElementType, TextAttributesKey>();
  private static final Map<IElementType, TextAttributesKey> POWER_SAVE_MODE_ATTRIBUTES = new HashMap<IElementType, TextAttributesKey>();


  static final TokenSet tBLOCK_COMMENTS = TokenSet.create(
      mML_COMMENT, GROOVY_DOC_COMMENT
  );

  static final TokenSet tLINE_COMMENTS = TokenSet.create(
      mSL_COMMENT,
      mSH_COMMENT
  );

  static final TokenSet tBAD_CHARACTERS = TokenSet.create(
      mWRONG
  );

  static final TokenSet tGSTRINGS = TokenSet.create(
      mGSTRING_BEGIN,
      mGSTRING_CONTENT,
      mGSTRING_END,
      mGSTRING_LITERAL
  );

  static final TokenSet tSTRINGS = TokenSet.create(
      mSTRING_LITERAL
  );

  static final TokenSet tBRACES = TokenSet.create(
    mLCURLY,
    mRCURLY
  );
  static final TokenSet tPARENTHESES = TokenSet.create(
    mLPAREN,
    mRPAREN
  );
  static final TokenSet tBRACKETS = TokenSet.create(
    mLBRACK,
    mRBRACK
  );

  static final TokenSet tOperators = TokenSet.orSet(BINARY_OP_SET, UNARY_OP_SET, ASSIGN_OP_SET);

  static {
    fillMap(ATTRIBUTES, tLINE_COMMENTS, DefaultHighlighter.LINE_COMMENT);
    fillMap(ATTRIBUTES, tBLOCK_COMMENTS, DefaultHighlighter.BLOCK_COMMENT);
    fillMap(ATTRIBUTES, tBAD_CHARACTERS, DefaultHighlighter.BAD_CHARACTER);
    fillMap(ATTRIBUTES, NUMBERS, DefaultHighlighter.NUMBER);
    fillMap(ATTRIBUTES, tGSTRINGS, DefaultHighlighter.GSTRING);
    fillMap(ATTRIBUTES, tSTRINGS, DefaultHighlighter.STRING);
    fillMap(ATTRIBUTES, DefaultHighlighter.STRING, mREGEX_BEGIN, mREGEX_CONTENT, mREGEX_END, mDOLLAR_SLASH_REGEX_BEGIN, mDOLLAR_SLASH_REGEX_CONTENT, mDOLLAR_SLASH_REGEX_END);
    fillMap(ATTRIBUTES, tBRACES, DefaultHighlighter.BRACES);
    fillMap(ATTRIBUTES, tBRACKETS, DefaultHighlighter.BRACKETS);
    fillMap(ATTRIBUTES, tPARENTHESES, DefaultHighlighter.PARENTHESES);
    fillMap(ATTRIBUTES, tOperators, DefaultHighlighter.OPERATION_SIGN);
    fillMap(ATTRIBUTES, DefaultHighlighter.VALID_STRING_ESCAPE, StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN);
    fillMap(ATTRIBUTES, DefaultHighlighter.INVALID_STRING_ESCAPE, StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN);
    fillMap(ATTRIBUTES, DefaultHighlighter.INVALID_STRING_ESCAPE, StringEscapesTokenTypes.INVALID_UNICODE_ESCAPE_TOKEN);
  }

  static {
    fillMap(POWER_SAVE_MODE_ATTRIBUTES, tLINE_COMMENTS, DefaultHighlighter.LINE_COMMENT);
    fillMap(POWER_SAVE_MODE_ATTRIBUTES, tBLOCK_COMMENTS, DefaultHighlighter.BLOCK_COMMENT);
    fillMap(POWER_SAVE_MODE_ATTRIBUTES, tBAD_CHARACTERS, DefaultHighlighter.BAD_CHARACTER);
    fillMap(POWER_SAVE_MODE_ATTRIBUTES, NUMBERS, DefaultHighlighter.NUMBER);
    fillMap(POWER_SAVE_MODE_ATTRIBUTES, tGSTRINGS, DefaultHighlighter.GSTRING);
    fillMap(POWER_SAVE_MODE_ATTRIBUTES, tSTRINGS, DefaultHighlighter.STRING);
    fillMap(POWER_SAVE_MODE_ATTRIBUTES, DefaultHighlighter.STRING, mREGEX_BEGIN, mREGEX_CONTENT, mREGEX_END, mDOLLAR_SLASH_REGEX_BEGIN,
            mDOLLAR_SLASH_REGEX_CONTENT, mDOLLAR_SLASH_REGEX_END);
    fillMap(POWER_SAVE_MODE_ATTRIBUTES, tBRACES, DefaultHighlighter.BRACES);
    fillMap(POWER_SAVE_MODE_ATTRIBUTES, tBRACKETS, DefaultHighlighter.BRACKETS);
    fillMap(POWER_SAVE_MODE_ATTRIBUTES, tPARENTHESES, DefaultHighlighter.PARENTHESES);
    fillMap(POWER_SAVE_MODE_ATTRIBUTES, DefaultHighlighter.VALID_STRING_ESCAPE, StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN);
    fillMap(POWER_SAVE_MODE_ATTRIBUTES, DefaultHighlighter.INVALID_STRING_ESCAPE, StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN);
    fillMap(POWER_SAVE_MODE_ATTRIBUTES, DefaultHighlighter.INVALID_STRING_ESCAPE, StringEscapesTokenTypes.INVALID_UNICODE_ESCAPE_TOKEN);
    fillMap(POWER_SAVE_MODE_ATTRIBUTES, KEYWORDS, DefaultHighlighter.KEYWORD);
  }

  @NotNull
  public Lexer getHighlightingLexer() {
    return new GroovyHighlightingLexer();
  }

  private static class GroovyHighlightingLexer extends LayeredLexer {
    private GroovyHighlightingLexer() {
      super(new GroovyLexer());
      registerSelfStoppingLayer(new StringLiteralLexer(StringLiteralLexer.NO_QUOTE_CHAR, GroovyTokenTypes.mSTRING_LITERAL, true, "$"),
                                new IElementType[]{GroovyTokenTypes.mSTRING_LITERAL}, IElementType.EMPTY_ARRAY);
      registerSelfStoppingLayer(new StringLiteralLexer(StringLiteralLexer.NO_QUOTE_CHAR, GroovyTokenTypes.mGSTRING_LITERAL, true, "$"),
                                new IElementType[]{GroovyTokenTypes.mGSTRING_LITERAL}, IElementType.EMPTY_ARRAY);
      registerSelfStoppingLayer(new StringLiteralLexer(StringLiteralLexer.NO_QUOTE_CHAR, GroovyTokenTypes.mGSTRING_CONTENT, true, "$"),
                                new IElementType[]{GroovyTokenTypes.mGSTRING_CONTENT}, IElementType.EMPTY_ARRAY);
      registerSelfStoppingLayer(new GroovySlashyStringLexer(), new IElementType[]{GroovyTokenTypes.mREGEX_CONTENT}, IElementType.EMPTY_ARRAY);
      registerSelfStoppingLayer(new GroovyDollarSlashyStringLexer(), new IElementType[]{GroovyTokenTypes.mDOLLAR_SLASH_REGEX_CONTENT}, IElementType.EMPTY_ARRAY);
    }
  }

  @NotNull
  public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
    return pack(PowerSaveMode.isEnabled() ? POWER_SAVE_MODE_ATTRIBUTES.get(tokenType) : ATTRIBUTES.get(tokenType));
  }
}
