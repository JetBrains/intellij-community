package com.intellij.util.plist;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.pbxproj.PbxLexer;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.util.pbxproj.PbxTokenType.*;

public class NSPlistParser extends SpecParser {
  public NSPlistParser() {
  }

  private static Plist createPList() {
    return new Plist();
  }

  public static Plist parse(final PbxLexer lexer) {
    for (int tries = 0; tries < 2; tries++) {
      IElementType type = lexer.getTokenType();
      if (type == LBRACE) {
        return parseObject(lexer, createPList());
      }
      if (type == LPAR) {
        List list = parseList(lexer);
        Plist result = createPList();
        for (int i = 0; i < list.size(); i++) {
          result.setAttribute("Item" + i, list.get(i));
        }
        return result;
      }
      lexer.nextToken();
    }

    throw unexpectedTokenError(lexer, LBRACE, LPAR);
  }

  public static Plist parseObject(PbxLexer lexer, final Plist plist) {
    expectAndNext(lexer, LBRACE);

    if (lexer.getTokenType() != RBRACE) {
      parseAttribute(lexer, plist);

      while (lexer.getTokenType() != RBRACE) {
        parseAttribute(lexer, plist);
      }
    }

    lexer.nextToken();

    return plist;
  }

  private static void parseAttribute(PbxLexer lexer, final Plist plist) {
    IElementType tt = lexer.getTokenType();
    String key = null;
    if (tt == VALUE || tt == STRING_LITERAL) {
      key = lexer.getTokenText();
      if (tt == STRING_LITERAL) {
        key = StringUtil.unescapeStringCharacters(key.substring(1, key.length() - 1));
      }
    }

    if (key == null) {
      throw error(lexer, "Object attribute key expected");
    }


    lexer.nextToken();

    expectAndNext(lexer, EQ);
    Object value = parseValue(lexer, createPList());

    expectAndNext(lexer, SEMICOLON);
    try {
      plist.setAttribute(key, value);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static Object parseValue(PbxLexer lexer, final Plist plist) {
    final IElementType tt = lexer.getTokenType();
    if (tt == LBRACE) {
      return parseObject(lexer, plist);
    }
    else if (tt == LPAR) {
      return parseList(lexer);
    }
    else if (tt == VALUE) {
      final String value = lexer.getTokenText();
      lexer.nextToken();
      return value;
    }
    else if (tt == STRING_LITERAL || tt == HEX_LITERAL) {
      final String text = lexer.getTokenText();
      lexer.nextToken();
      return StringUtil.unescapeStringCharacters(text.substring(1, text.length() - 1));
    }

    throw error(lexer, "Cannot parse attribute value, unexpectedToken");
  }

  private static List<Object> parseList(PbxLexer lexer) {
    expectAndNext(lexer, LPAR);

    List<Object> answer = new ArrayList<>();
    do {
      if (lexer.getTokenType() == RPAR) break;
      answer.add(parseValue(lexer, createPList()));
      if (lexer.getTokenType() == COMMA) lexer.nextToken();
    }
    while (true);

    expectAndNext(lexer, RPAR);
    return answer;
  }
}
