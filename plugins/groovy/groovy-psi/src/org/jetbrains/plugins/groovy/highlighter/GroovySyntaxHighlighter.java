// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.highlighter;

import com.intellij.ide.PowerSaveMode;
import com.intellij.ide.highlighter.JavaHighlightingColors;
import com.intellij.lexer.LayeredLexer;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.StringLiteralLexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.StringEscapesTokenTypes;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.groovydoc.parser.GroovyDocElementTypes;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyLexer;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;

import java.util.HashMap;
import java.util.Map;

import static com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.*;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyTokenSets.ASSIGNMENTS;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyTokenSets.BINARY_OPERATORS;

public class GroovySyntaxHighlighter extends SyntaxHighlighterBase {

  public static final TextAttributesKey LINE_COMMENT = createTextAttributesKey("Line comment", DefaultLanguageHighlighterColors.LINE_COMMENT);
  public static final TextAttributesKey ANNOTATION = createTextAttributesKey("Annotation", JavaHighlightingColors.ANNOTATION_NAME_ATTRIBUTES);
  public static final TextAttributesKey ANNOTATION_ATTRIBUTE_NAME = createTextAttributesKey("Anotation attribute name", JavaHighlightingColors.ANNOTATION_ATTRIBUTE_NAME_ATTRIBUTES);
  public static final TextAttributesKey LOCAL_VARIABLE = createTextAttributesKey("Groovy var", JavaHighlightingColors.LOCAL_VARIABLE_ATTRIBUTES);
  public static final TextAttributesKey REASSIGNED_LOCAL_VARIABLE = createTextAttributesKey("Groovy reassigned var", JavaHighlightingColors.REASSIGNED_LOCAL_VARIABLE_ATTRIBUTES);
  public static final TextAttributesKey PARAMETER = createTextAttributesKey("Groovy parameter", JavaHighlightingColors.PARAMETER_ATTRIBUTES);
  public static final TextAttributesKey REASSIGNED_PARAMETER = createTextAttributesKey("Groovy reassigned parameter", JavaHighlightingColors.REASSIGNED_PARAMETER_ATTRIBUTES);
  public static final TextAttributesKey METHOD_DECLARATION = createTextAttributesKey("Groovy method declaration", JavaHighlightingColors.METHOD_DECLARATION_ATTRIBUTES);
  public static final TextAttributesKey CONSTRUCTOR_DECLARATION = createTextAttributesKey("Groovy constructor declaration", JavaHighlightingColors.CONSTRUCTOR_DECLARATION_ATTRIBUTES);
  public static final TextAttributesKey INSTANCE_FIELD = createTextAttributesKey("Instance field", JavaHighlightingColors.INSTANCE_FIELD_ATTRIBUTES);
  public static final TextAttributesKey METHOD_CALL = createTextAttributesKey("Method call", JavaHighlightingColors.METHOD_CALL_ATTRIBUTES);
  public static final TextAttributesKey CONSTRUCTOR_CALL = createTextAttributesKey("Groovy constructor call", JavaHighlightingColors.CONSTRUCTOR_CALL_ATTRIBUTES);
  public static final TextAttributesKey STATIC_FIELD = createTextAttributesKey("Static field", JavaHighlightingColors.STATIC_FINAL_FIELD_ATTRIBUTES);
  public static final TextAttributesKey STATIC_METHOD_ACCESS = createTextAttributesKey("Static method access", JavaHighlightingColors.STATIC_METHOD_ATTRIBUTES);
  public static final TextAttributesKey BLOCK_COMMENT = createTextAttributesKey("Block comment", JavaHighlightingColors.JAVA_BLOCK_COMMENT);
  public static final TextAttributesKey DOC_COMMENT_CONTENT = createTextAttributesKey("Groovydoc comment", JavaHighlightingColors.DOC_COMMENT);
  public static final TextAttributesKey DOC_COMMENT_TAG = createTextAttributesKey("Groovydoc tag", JavaHighlightingColors.DOC_COMMENT_TAG);

  public static final TextAttributesKey CLASS_REFERENCE = createTextAttributesKey("Class", JavaHighlightingColors.CLASS_NAME_ATTRIBUTES);
  public static final TextAttributesKey ABSTRACT_CLASS_NAME = createTextAttributesKey("Abstract class name", CLASS_REFERENCE);
  public static final TextAttributesKey ANONYMOUS_CLASS_NAME = createTextAttributesKey("Anonymous class name", CLASS_REFERENCE);
  public static final TextAttributesKey INTERFACE_NAME = createTextAttributesKey("Interface name", JavaHighlightingColors.INTERFACE_NAME_ATTRIBUTES);
  public static final TextAttributesKey TRAIT_NAME = createTextAttributesKey("Trait name", INTERFACE_NAME);
  public static final TextAttributesKey ENUM_NAME = createTextAttributesKey("Enum name", CLASS_REFERENCE);
  public static final TextAttributesKey TYPE_PARAMETER = createTextAttributesKey("Type parameter", JavaHighlightingColors.TYPE_PARAMETER_NAME_ATTRIBUTES);

  public static final TextAttributesKey INSTANCE_PROPERTY_REFERENCE = createTextAttributesKey("Instance property reference ID", JavaHighlightingColors.INSTANCE_FIELD_ATTRIBUTES);
  public static final TextAttributesKey STATIC_PROPERTY_REFERENCE = createTextAttributesKey("Static property reference ID", JavaHighlightingColors.STATIC_FINAL_FIELD_ATTRIBUTES);

  public static final TextAttributesKey KEYWORD = createTextAttributesKey("GROOVY_KEYWORD", JavaHighlightingColors.KEYWORD);
  public static final TextAttributesKey NUMBER = createTextAttributesKey("Number", JavaHighlightingColors.NUMBER);
  public static final TextAttributesKey GSTRING = createTextAttributesKey("GString", JavaHighlightingColors.STRING);
  public static final TextAttributesKey STRING = createTextAttributesKey("String", JavaHighlightingColors.STRING);
  public static final TextAttributesKey BRACES = createTextAttributesKey("Braces", JavaHighlightingColors.BRACES);
  public static final TextAttributesKey CLOSURE_ARROW_AND_BRACES = createTextAttributesKey("Closure braces", BRACES);
  public static final TextAttributesKey LAMBDA_ARROW_AND_BRACES = createTextAttributesKey("Lambda braces", BRACES);
  public static final TextAttributesKey BRACKETS = createTextAttributesKey("Brackets", JavaHighlightingColors.BRACKETS);
  public static final TextAttributesKey PARENTHESES = createTextAttributesKey("Parentheses", JavaHighlightingColors.PARENTHESES);
  public static final TextAttributesKey OPERATION_SIGN = createTextAttributesKey("Operation sign", JavaHighlightingColors.OPERATION_SIGN);
  public static final TextAttributesKey BAD_CHARACTER = createTextAttributesKey("Bad character", HighlighterColors.BAD_CHARACTER);

  public static final TextAttributesKey UNRESOLVED_ACCESS = createTextAttributesKey("Unresolved reference access", DefaultLanguageHighlighterColors.IDENTIFIER);
  public static final TextAttributesKey LITERAL_CONVERSION = createTextAttributesKey("List/map to object conversion", JavaHighlightingColors.NUMBER);

  public static final TextAttributesKey MAP_KEY = createTextAttributesKey("Map key", JavaHighlightingColors.STRING);
  public static final TextAttributesKey VALID_STRING_ESCAPE = createTextAttributesKey("Valid string escape", JavaHighlightingColors.VALID_STRING_ESCAPE);
  public static final TextAttributesKey INVALID_STRING_ESCAPE = createTextAttributesKey("Invalid string escape", JavaHighlightingColors.INVALID_STRING_ESCAPE);
  public static final TextAttributesKey LABEL = createTextAttributesKey("Label", DefaultLanguageHighlighterColors.LABEL);

  private static final Map<IElementType, TextAttributesKey> ATTRIBUTES = new HashMap<>();
  private static final Map<IElementType, TextAttributesKey> POWER_SAVE_MODE_ATTRIBUTES = new HashMap<>();


  static final TokenSet tBLOCK_COMMENTS = TokenSet.create(
    GroovyTokenTypes.mML_COMMENT, GroovyDocElementTypes.GROOVY_DOC_COMMENT
  );

  static final TokenSet tLINE_COMMENTS = TokenSet.create(
    GroovyTokenTypes.mSL_COMMENT,
    GroovyTokenTypes.mSH_COMMENT
  );

  static final TokenSet tBAD_CHARACTERS = TokenSet.create(
    GroovyTokenTypes.mWRONG
  );

  static final TokenSet tGSTRINGS = TokenSet.create(
    GroovyTokenTypes.mGSTRING_BEGIN,
    GroovyTokenTypes.mGSTRING_CONTENT,
    GroovyTokenTypes.mGSTRING_END,
    STRING_DQ,
    STRING_TDQ
  );

  static final TokenSet tSTRINGS = TokenSet.create(
    STRING_SQ,
    STRING_TSQ
  );

  static final TokenSet tBRACES = TokenSet.create(
    GroovyTokenTypes.mLCURLY,
    GroovyTokenTypes.mRCURLY
  );
  static final TokenSet tPARENTHESES = TokenSet.create(
    GroovyTokenTypes.mLPAREN,
    GroovyTokenTypes.mRPAREN
  );
  static final TokenSet tBRACKETS = TokenSet.create(
    GroovyTokenTypes.mLBRACK,
    GroovyTokenTypes.mRBRACK
  );

  static final TokenSet tOperators = TokenSet.orSet(BINARY_OPERATORS, TokenSets.UNARY_OP_SET, ASSIGNMENTS);

  static {
    fillMap(ATTRIBUTES, tLINE_COMMENTS, LINE_COMMENT);
    fillMap(ATTRIBUTES, tBLOCK_COMMENTS, BLOCK_COMMENT);
    fillMap(ATTRIBUTES, tBAD_CHARACTERS, BAD_CHARACTER);
    fillMap(ATTRIBUTES, TokenSets.NUMBERS, NUMBER);
    fillMap(ATTRIBUTES, tGSTRINGS, GSTRING);
    fillMap(ATTRIBUTES, tSTRINGS, STRING);
    fillMap(ATTRIBUTES, STRING, GroovyTokenTypes.mREGEX_BEGIN, GroovyTokenTypes.mREGEX_CONTENT,
            GroovyTokenTypes.mREGEX_END, GroovyTokenTypes.mDOLLAR_SLASH_REGEX_BEGIN, GroovyTokenTypes.mDOLLAR_SLASH_REGEX_CONTENT,
            GroovyTokenTypes.mDOLLAR_SLASH_REGEX_END);
    fillMap(ATTRIBUTES, tBRACES, BRACES);
    fillMap(ATTRIBUTES, tBRACKETS, BRACKETS);
    fillMap(ATTRIBUTES, tPARENTHESES, PARENTHESES);
    fillMap(ATTRIBUTES, tOperators, OPERATION_SIGN);
    fillMap(ATTRIBUTES, VALID_STRING_ESCAPE, StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN);
    fillMap(ATTRIBUTES, INVALID_STRING_ESCAPE, StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN);
    fillMap(ATTRIBUTES, INVALID_STRING_ESCAPE, StringEscapesTokenTypes.INVALID_UNICODE_ESCAPE_TOKEN);
    fillMap(ATTRIBUTES, TokenSets.KEYWORDS, KEYWORD);
  }

  static {
    fillMap(POWER_SAVE_MODE_ATTRIBUTES, tLINE_COMMENTS, LINE_COMMENT);
    fillMap(POWER_SAVE_MODE_ATTRIBUTES, tBLOCK_COMMENTS, BLOCK_COMMENT);
    fillMap(POWER_SAVE_MODE_ATTRIBUTES, tBAD_CHARACTERS, BAD_CHARACTER);
    fillMap(POWER_SAVE_MODE_ATTRIBUTES, TokenSets.NUMBERS, NUMBER);
    fillMap(POWER_SAVE_MODE_ATTRIBUTES, tGSTRINGS, GSTRING);
    fillMap(POWER_SAVE_MODE_ATTRIBUTES, tSTRINGS, STRING);
    fillMap(POWER_SAVE_MODE_ATTRIBUTES, STRING, GroovyTokenTypes.mREGEX_BEGIN, GroovyTokenTypes.mREGEX_CONTENT,
            GroovyTokenTypes.mREGEX_END, GroovyTokenTypes.mDOLLAR_SLASH_REGEX_BEGIN,
            GroovyTokenTypes.mDOLLAR_SLASH_REGEX_CONTENT, GroovyTokenTypes.mDOLLAR_SLASH_REGEX_END);
    fillMap(POWER_SAVE_MODE_ATTRIBUTES, tBRACES, BRACES);
    fillMap(POWER_SAVE_MODE_ATTRIBUTES, tBRACKETS, BRACKETS);
    fillMap(POWER_SAVE_MODE_ATTRIBUTES, tPARENTHESES, PARENTHESES);
    fillMap(POWER_SAVE_MODE_ATTRIBUTES, VALID_STRING_ESCAPE, StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN);
    fillMap(POWER_SAVE_MODE_ATTRIBUTES, INVALID_STRING_ESCAPE, StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN);
    fillMap(POWER_SAVE_MODE_ATTRIBUTES, INVALID_STRING_ESCAPE, StringEscapesTokenTypes.INVALID_UNICODE_ESCAPE_TOKEN);
    fillMap(POWER_SAVE_MODE_ATTRIBUTES, TokenSets.KEYWORDS, KEYWORD);
  }

  @Override
  @NotNull
  public Lexer getHighlightingLexer() {
    return new GroovyHighlightingLexer();
  }

  private static final class GroovyHighlightingLexer extends LayeredLexer {
    private GroovyHighlightingLexer() {
      super(new GroovyLexer());
      registerSelfStoppingLayer(new StringLiteralLexer(StringLiteralLexer.NO_QUOTE_CHAR, STRING_SQ, true, "$"),
                                new IElementType[]{STRING_SQ}, IElementType.EMPTY_ARRAY);
      registerSelfStoppingLayer(new StringLiteralLexer(StringLiteralLexer.NO_QUOTE_CHAR, STRING_TSQ, true, "$"),
                                new IElementType[]{STRING_TSQ}, IElementType.EMPTY_ARRAY);
      registerSelfStoppingLayer(new StringLiteralLexer(StringLiteralLexer.NO_QUOTE_CHAR, STRING_DQ, true, "$"),
                                new IElementType[]{STRING_DQ}, IElementType.EMPTY_ARRAY);
      registerSelfStoppingLayer(new StringLiteralLexer(StringLiteralLexer.NO_QUOTE_CHAR, STRING_TDQ, true, "$"),
                                new IElementType[]{STRING_TDQ}, IElementType.EMPTY_ARRAY);
      registerSelfStoppingLayer(new StringLiteralLexer(StringLiteralLexer.NO_QUOTE_CHAR, GroovyTokenTypes.mGSTRING_CONTENT, true, "$"),
                                new IElementType[]{GroovyTokenTypes.mGSTRING_CONTENT}, IElementType.EMPTY_ARRAY);
      registerSelfStoppingLayer(new GroovySlashyStringLexer(), new IElementType[]{GroovyTokenTypes.mREGEX_CONTENT}, IElementType.EMPTY_ARRAY);
      registerSelfStoppingLayer(new GroovyDollarSlashyStringLexer(), new IElementType[]{GroovyTokenTypes.mDOLLAR_SLASH_REGEX_CONTENT}, IElementType.EMPTY_ARRAY);
    }
  }

  @Override
  public TextAttributesKey @NotNull [] getTokenHighlights(IElementType tokenType) {
    return pack(PowerSaveMode.isEnabled() ? POWER_SAVE_MODE_ATTRIBUTES.get(tokenType) : ATTRIBUTES.get(tokenType));
  }
}
