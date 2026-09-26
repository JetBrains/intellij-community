// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.fxml.refs;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parser for FXML expression-binding bodies (the text between <code>${</code> and <code>}</code>).
 *
 * <p>The FXML expression grammar supports: dotted property chains, string / numeric / boolean / null literals,
 * unary {@code !} and {@code -}, binary {@code + - * / %}, {@code && ||}, comparisons {@code == != < > <= >=},
 * and parenthesized sub-expressions.
 *
 * <p>This parser is reference-oriented: it does not build an AST. It returns the list of property-chain
 * operands (each with per-segment offsets so PSI references can be placed) and a single
 * {@code syntacticallyValid} flag. Type checking is not done here.
 *
 * <p>Input is the raw value from {@code XmlAttributeValue.getValue()}, which does not decode XML entity
 * references. This parser understands {@code &amp;}, {@code &lt;}, {@code &gt;}, {@code &quot;},
 * {@code &apos;} as the corresponding single characters while preserving original offsets for segments.
 */
public final class JavaFxExpressionParser {

  private JavaFxExpressionParser() { }

  /** Single dotted property navigation, e.g. {@code a.b.c}. */
  public static final class PropertyChain {
    public final @NotNull List<Segment> segments;
    public final boolean incomplete;

    PropertyChain(@NotNull List<Segment> segments, boolean incomplete) {
      this.segments = segments;
      this.incomplete = incomplete;
    }
  }

  /** One identifier within a chain, plus its 0-based offset inside the expression body. */
  public static final class Segment {
    public final @NotNull String name;
    public final int offsetInBody;

    Segment(@NotNull String name, int offsetInBody) {
      this.name = name;
      this.offsetInBody = offsetInBody;
    }
  }

  public static final class ParsedBinding {
    public final @NotNull List<PropertyChain> chains;
    public final boolean syntacticallyValid;
    public final boolean hasNonChainTokens;

    ParsedBinding(@NotNull List<PropertyChain> chains, boolean valid, boolean hasNonChainTokens) {
      this.chains = chains;
      this.syntacticallyValid = valid;
      this.hasNonChainTokens = hasNonChainTokens;
    }
  }

  /**
   * When the trailing <code>}</code> is missing (incomplete binding), pass whatever follows <code>${</code>.
   */
  public static @NotNull ParsedBinding parse(@NotNull String body) {
    if (body.isEmpty()) {
      return new ParsedBinding(Collections.emptyList(), false, false);
    }
    Parser p = new Parser(body);
    p.parseAll();
    return new ParsedBinding(p.chains, p.valid, p.hasNonChainTokens);
  }

  private static final class Parser {
    /** Decoded character stream */
    private final char[] chars;
    /** For each decoded char, the starting offset in the original (raw) body */
    private final int[] origStart;
    private final int len;
    private int pos;
    private int parenDepth;
    private boolean expectOperand = true;

    final List<PropertyChain> chains = new ArrayList<>();
    boolean valid = true;
    boolean hasNonChainTokens;

    Parser(String src) {
      int n = src.length();
      char[] cs = new char[n];
      int[] os = new int[n];
      int out = 0;
      int i = 0;
      while (i < n) {
        if (src.charAt(i) == '&') {
          int decoded = matchEntity(src, i);
          if (decoded != -1) {
            cs[out] = (char)((decoded >>> 16) & 0xFFFF);
            os[out] = i;
            out++;
            i += decoded & 0xFFFF;
            continue;
          }
        }
        cs[out] = src.charAt(i);
        os[out] = i;
        out++;
        i++;
      }
      this.chars = cs;
      this.origStart = os;
      this.len = out;
    }

    /** Returns -1 if there is no match, else 16 bits with the entity followed by 16 bits for the length. */
    private static int matchEntity(String src, int i) {
      if (src.startsWith("&amp;", i))  return ('&' << 16) | 5;
      if (src.startsWith("&lt;", i))   return ('<' << 16) | 4;
      if (src.startsWith("&gt;", i))   return ('>' << 16) | 4;
      if (src.startsWith("&quot;", i)) return ('"' << 16) | 6;
      if (src.startsWith("&apos;", i)) return ('\'' << 16) | 6;
      return -1;
    }

    void parseAll() {
      while (pos < len) {
        skipWhitespace();
        if (pos >= len) break;

        char c = chars[pos];
        if (c == '(') {
          parenDepth++;
          pos++;
          expectOperand = true;
          hasNonChainTokens = true;
        }
        else if (c == ')') {
          if (parenDepth == 0 || expectOperand) valid = false;
          else parenDepth--;
          pos++;
          expectOperand = false;
          hasNonChainTokens = true;
        }
        else if (c == '"' || c == '\'') {
          if (!expectOperand) valid = false;
          if (!consumeStringLiteral(c)) valid = false;
          expectOperand = false;
          hasNonChainTokens = true;
        }
        else if (isDigit(c) || (c == '.' && pos + 1 < len && isDigit(chars[pos + 1]))) {
          if (!expectOperand) valid = false;
          consumeNumberLiteral();
          expectOperand = false;
          hasNonChainTokens = true;
        }
        else if (isIdentStart(c)) {
          if (!expectOperand) valid = false;
          consumeChainOrKeyword();
          expectOperand = false;
        }
        else if (isOperatorStart(c)) {
          if (c != '-' && c != '!' && expectOperand) valid = false;
          consumeOperator();
          expectOperand = true;
          hasNonChainTokens = true;
        }
        else {
          valid = false;
          pos++;
        }
      }
      if (parenDepth != 0) valid = false;
      if (expectOperand) {
        valid = false;
      }
    }

    private void skipWhitespace() {
      while (pos < len && isWhitespace(chars[pos])) pos++;
    }

    private boolean consumeStringLiteral(char quote) {
      pos++;
      while (pos < len) {
        char c = chars[pos];
        if (c == '\\' && pos + 1 < len) {
          pos += 2;
          continue;
        }
        if (c == quote) {
          pos++;
          return true;
        }
        pos++;
      }
      return false;
    }

    private void consumeNumberLiteral() {
      while (pos < len && isDigit(chars[pos])) pos++;
      if (pos < len && chars[pos] == '.') {
        do {
          pos++;
        }
        while (pos < len && isDigit(chars[pos]));
      }
      if (pos < len && (chars[pos] == 'e' || chars[pos] == 'E')) {
        pos++;
        if (pos < len && (chars[pos] == '+' || chars[pos] == '-')) pos++;
        boolean anyDigit = false;
        while (pos < len && isDigit(chars[pos])) {
          pos++;
          anyDigit = true;
        }
        if (!anyDigit) valid = false;
      }
    }

    private void consumeChainOrKeyword() {
      int identStartLogical = pos;
      consumeIdent();
      String firstIdent = identTextAt(identStartLogical);

      boolean followedByDot = pos < len && chars[pos] == '.';
      if (!followedByDot && isReservedKeyword(firstIdent)) {
        hasNonChainTokens = true;
        return;
      }

      List<Segment> segments = new ArrayList<>();
      segments.add(new Segment(firstIdent, origStart[identStartLogical]));
      boolean incomplete = false;
      while (pos < len && chars[pos] == '.') {
        pos++;
        if (pos >= len || !isIdentStart(chars[pos])) {
          incomplete = true;
          valid = false;
          break;
        }
        int segStart = pos;
        consumeIdent();
        segments.add(new Segment(identTextAt(segStart), origStart[segStart]));
      }
      chains.add(new PropertyChain(segments, incomplete));
    }

    private String identTextAt(int startLogical) {
      return new String(chars, startLogical, pos - startLogical);
    }

    private void consumeIdent() {
      while (pos < len && isIdentPart(chars[pos])) pos++;
    }

    private void consumeOperator() {
      char c = chars[pos];
      char next = pos + 1 < len ? chars[pos + 1] : '\0';
      switch (c) {
        case '&' -> { if (next == '&') pos += 2; else { valid = false; pos++; } }
        case '|' -> { if (next == '|') pos += 2; else { valid = false; pos++; } }
        case '=' -> { if (next == '=') pos += 2; else { valid = false; pos++; } }
        case '!', '<', '>' -> pos += (next == '=') ? 2 : 1;
        case '+', '-', '*', '/', '%' -> pos++;
        default -> { valid = false; pos++; }
      }
    }

    private static boolean isReservedKeyword(String s) {
      return "true".equals(s) || "false".equals(s) || "null".equals(s);
    }

    private static boolean isWhitespace(char c) {
      return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f';
    }

    private static boolean isDigit(char c) {
      return c >= '0' && c <= '9';
    }

    private static boolean isIdentStart(char c) {
      return Character.isJavaIdentifierStart(c);
    }

    private static boolean isIdentPart(char c) {
      return Character.isJavaIdentifierPart(c);
    }

    private static boolean isOperatorStart(char c) {
      return c == '+' || c == '-' || c == '*' || c == '/' || c == '%'
             || c == '!' || c == '&' || c == '|'
             || c == '<' || c == '>' || c == '=';
    }
  }
}
