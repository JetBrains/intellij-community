package com.intellij.util.plist;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.pbxproj.PbxLexer;

import java.util.Arrays;

public class SpecParser {
  protected static void expect(PbxLexer lexer, IElementType... tokens) {
    IElementType type = lexer.getTokenType();
    if (!Arrays.asList(tokens).contains(type)) {
      throw unexpectedTokenError(lexer, tokens);
    }
  }

  protected static void expectAndNext(PbxLexer lexer, IElementType... tokens) {
    expect(lexer, tokens);
    lexer.nextToken();
  }

  public static SpecParsingException unexpectedTokenError(PbxLexer lexer, IElementType... expectedTokens) {
    throw error(lexer, "Expecting tokens " + StringUtil.join(expectedTokens, each -> each.toString(), ", "));
  }

  protected static SpecParsingException error(PbxLexer lexer, String message) {
    int pos = lexer.getTokenStart();
    CharSequence text = lexer.getBufferSequence();
    StringBuilder builder = new StringBuilder();
    builder
      .append(message)
      .append("\nposition: ").append(pos)
      .append("\ncurrent token: '").append(lexer.getTokenText()).append("' type: ").append(lexer.getTokenType())
      .append("\n-------------------------------------\n")
      .append(text.subSequence(Math.max(0, pos - 500), pos))
      .append("<current lexer position>")
      .append(text.subSequence(pos, Math.min(text.length(), pos + 500)))
      .append("\n-------------------------------------");

    throw new SpecParsingException(builder.toString());
  }
}
