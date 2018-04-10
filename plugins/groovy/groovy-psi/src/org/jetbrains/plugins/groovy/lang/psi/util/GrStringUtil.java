/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.util;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.*;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.literals.GrLiteralImpl;

/**
 * @author Maxim.Medvedev
 */
public class GrStringUtil {
  private static final Logger LOG = Logger.getInstance(GrStringUtil.class);

  public static final String TRIPLE_QUOTES = "'''";
  public static final String QUOTE = "'";
  public static final String DOUBLE_QUOTES = "\"";
  public static final String TRIPLE_DOUBLE_QUOTES = "\"\"\"";
  public static final String SLASH = "/";
  public static final String DOLLAR_SLASH = "$/";
  public static final String SLASH_DOLLAR = "/$";

  private GrStringUtil() {
  }

  public static String unescapeString(String s) {
    final int length = s.length();
    StringBuilder buffer = new StringBuilder(length);
    boolean escaped = false;
    for (int idx = 0; idx < length; idx++) {
      char ch = s.charAt(idx);
      if (!escaped) {
        if (ch == '\\') {
          escaped = true;
        }
        else {
          buffer.append(ch);
        }
      }
      else {
        switch (ch) {
          case 'n':
            buffer.append('\n');
            break;

          case 'r':
            buffer.append('\r');
            break;

          case 'b':
            buffer.append('\b');
            break;

          case 't':
            buffer.append('\t');
            break;

          case 'f':
            buffer.append('\f');
            break;

          case '\'':
            buffer.append('\'');
            break;

          case '\"':
            buffer.append('\"');
            break;

          case '\\':
            buffer.append('\\');
            break;
          case '\n':
            //do nothing
            break;

          case 'u':
            if (idx + 4 < length) {
              try {
                int code = Integer.valueOf(s.substring(idx + 1, idx + 5), 16).intValue();
                idx += 4;
                buffer.append((char)code);
              }
              catch (NumberFormatException e) {
                buffer.append("\\u");
              }
            }
            else {
              buffer.append("\\u");
            }
            break;

          default:
            buffer.append('\\');
            buffer.append(ch);
            break;
        }
        escaped = false;
      }
    }

    return buffer.toString();
  }

  public static String unescapeSlashyString(String s) {
    return unescapeRegex(s, true);
  }

  public static String unescapeDollarSlashyString(String s) {
    return unescapeRegex(s, false);
  }

  private static String unescapeRegex(String s, boolean unescapeSlash) {
    final int length = s.length();
    StringBuilder buffer = new StringBuilder(length);
    
    boolean escaped = false;
    for (int idx = 0; idx < length; idx++) {
      char ch = s.charAt(idx);
      if (!escaped) {
        if (ch == '\\') {
          escaped = true;
        }
        else {
          buffer.append(ch);
        }
      }
      else {
        switch (ch) {
          case '/':
            if (!unescapeSlash) {
              buffer.append('\\');
            }
            buffer.append('/');
            break;
          case 'u':
            if (idx + 4 < length) {
              try {
                int code = Integer.valueOf(s.substring(idx + 1, idx + 5), 16).intValue();
                idx += 4;
                buffer.append((char)code);
              }
              catch (NumberFormatException e) {
                buffer.append("\\u");
              }
            }
            else {
              buffer.append("\\u");
            }
            break;

          default:
            buffer.append('\\');
            buffer.append(ch);
            break;
        }
        escaped = false;
      }
    }

    if (escaped) buffer.append('\\');
    return buffer.toString();
  }

  public static String escapeSymbolsForSlashyStrings(String str) {
    final StringBuilder buffer = new StringBuilder(str.length());
    escapeSymbolsForSlashyStrings(buffer, str);
    return buffer.toString();
  }
  
  public static void escapeSymbolsForSlashyStrings(StringBuilder buffer, String str) {
    final int length = str.length();
    for (int idx = 0; idx < length; idx++) {
      char ch = str.charAt(idx);
      switch (ch) {
        case '/':
          buffer.append("\\/");
          break;
        default:
          if (Character.isISOControl(ch) || ch == '$') {
            appendUnicode(buffer, ch);
          }
          else {
            buffer.append(ch);
          }
      }
    }
  }

  public static String escapeSymbolsForDollarSlashyStrings(String str) {
    final StringBuilder buffer = new StringBuilder(str.length());
    escapeSymbolsForDollarSlashyStrings(buffer, str);
    return buffer.toString();
  }
  
  public static void escapeSymbolsForDollarSlashyStrings(StringBuilder buffer, String str) {
    final int length = str.length();
    for (int idx = 0; idx < length; idx++) {
      char ch = str.charAt(idx);
      switch (ch) {
        case '/':
          if (idx + 1 < length && str.charAt(idx + 1) == '$') {
            appendUnicode(buffer, '/');
            appendUnicode(buffer, '$');
            break;
          }
        default:
          if (Character.isISOControl(ch)) {
            appendUnicode(buffer, ch);
          }
          else {
            buffer.append(ch);
          }
      }
    }
  }

  private static void appendUnicode(StringBuilder buffer, char ch) {
    String hexCode = Integer.toHexString(ch).toUpperCase();
    buffer.append("\\u");
    int paddingCount = 4 - hexCode.length();
    while (paddingCount-- > 0) {
      buffer.append(0);
    }
    buffer.append(hexCode);
  }

  public static String escapeSymbolsForGString(CharSequence s, boolean isSingleLine, boolean unescapeSymbols) {
    StringBuilder b = new StringBuilder();
    escapeSymbolsForGString(s, isSingleLine, unescapeSymbols, b);
    return b.toString();
  }

  public static void escapeSymbolsForGString(CharSequence s, boolean isSingleLine, boolean unescapeSymbols, StringBuilder b) {
    escapeStringCharacters(s.length(), s, isSingleLine ? "$\"" : "$", isSingleLine, true, b);
    if (unescapeSymbols) {
      unescapeCharacters(b, isSingleLine ? "'" : "'\"", true);
    }
    if (!isSingleLine) escapeLastSymbols(b, '\"');
  }

  public static String escapeSymbolsForString(String s, boolean isSingleLine, boolean forInjection) {
    final StringBuilder builder = new StringBuilder();
    escapeStringCharacters(s.length(), s, isSingleLine ? "'" : "", isSingleLine, true, builder);
    if (!forInjection) {
      unescapeCharacters(builder, isSingleLine ? "$\"" : "$'\"", true);
    }
    if (!isSingleLine) escapeLastSymbols(builder, '\'');
    return builder.toString();
  }

  private static void escapeLastSymbols(StringBuilder builder, char toEscape) {
    for (int i = builder.length() - 1; i >= 0 && builder.charAt(i) == toEscape; i--) {
      builder.insert(i, '\\');
    }
  }

  @NotNull
  public static StringBuilder escapeStringCharacters(int length,
                                                     @NotNull CharSequence str,
                                                     @Nullable String additionalChars,
                                                     boolean escapeLineFeeds,
                                                     boolean escapeBackSlash,
                                                     @NotNull @NonNls StringBuilder buffer) {
    for (int idx = 0; idx < length; idx++) {
      char ch = str.charAt(idx);
      switch (ch) {
        case '\b':
          buffer.append("\\b");
          break;

        case '\t':
          buffer.append("\\t");
          break;

        case '\f':
          buffer.append("\\f");
          break;

        case '\\':
          if (escapeBackSlash) {
            buffer.append("\\\\");
          }
          else {
            buffer.append('\\');
          }
          break;

        case '\n':
          if (escapeLineFeeds) {
            buffer.append("\\n");
          }
          else {
            buffer.append('\n');
          }
          break;

        case '\r':
          if (escapeLineFeeds) {
            buffer.append("\\r");
          }
          else {
            buffer.append('\r');
          }
          break;

        default:
          if (additionalChars != null && additionalChars.indexOf(ch) > -1) {
            buffer.append("\\").append(ch);
          }
          else if (Character.isISOControl(ch)) {
            appendUnicode(buffer, ch);
          }
          else {
            buffer.append(ch);
          }
      }
    }
    return buffer;
  }



  public static void unescapeCharacters(StringBuilder builder, String toUnescape, boolean isMultiLine) {
    for (int i = 0; i < builder.length(); i++) {
      if (builder.charAt(i) != '\\') continue;
      if (i + 1 == builder.length()) break;
      char next = builder.charAt(i + 1);
      if (next == 'n') {
        if (isMultiLine) {
          builder.replace(i, i + 2, "\n");
        }
      }
      else if (next == 'r') {
        if (isMultiLine) {
          builder.replace(i, i + 2, "\r");
        }
      }
      else if (toUnescape.indexOf(next) != -1) {
        builder.delete(i, i + 1);
      }
      else {
        i++;
      }
    }
  }

  public static String escapeAndUnescapeSymbols(String s, String toEscape, String toUnescape, StringBuilder builder) {
    boolean escaped = false;
    for (int i = 0; i < s.length(); i++) {
      char ch = s.charAt(i);
      if (escaped) {
        if (toUnescape.indexOf(ch) < 0) {
          builder.append('\\');
          builder.append(ch);
        }
        else {
          if (ch=='n') builder.append('\n');
          else if (ch=='r') builder.append('\r');
          else if (ch=='b') builder.append('\b');
          else if (ch=='t') builder.append('\t');
          else if (ch=='f') builder.append('\r');
          else builder.append(ch);
        }
        escaped = false;
        continue;
      }
      if (ch == '\\') {
        escaped = true;
        continue;
      }

      if (toEscape.indexOf(ch) >= 0) {
        builder.append('\\');
        if (ch == '\n') ch = 'n';
        else if (ch == '\b') ch = 'b';
        else if (ch == '\t') ch = 't';
        else if (ch == '\r') ch = 'r';
        else if (ch == '\f') ch = 'f';
      }

      builder.append(ch);
    }
    return builder.toString();
  }

  public static String removeQuotes(@NotNull String s) {
    String quote = getStartQuote(s);
    int sL = s.length();
    int qL = quote.length();
    if (sL >= qL * 2 && DOLLAR_SLASH.equals(quote)) {
      if (s.endsWith(SLASH_DOLLAR)) {
        return s.substring(qL, sL - qL);
      }
      else {
        return s.substring(qL);
      }
    }

    if (sL >= qL * 2 && s.endsWith(quote)) {
      return s.substring(qL, sL - qL);
    }
    else {
      return s.substring(qL);
    }
  }

  public static String addQuotes(String s, boolean forGString) {
    if (forGString) {
      if (s.contains("\n") || s.contains("\r")) {
        return TRIPLE_DOUBLE_QUOTES + s + TRIPLE_DOUBLE_QUOTES;
      }
      else {
        return DOUBLE_QUOTES + s + DOUBLE_QUOTES;
      }
    }
    else {
      if (s.contains("\n") || s.contains("\r")) {
        return TRIPLE_QUOTES + s + TRIPLE_QUOTES;
      }
      else {
        return QUOTE + s + QUOTE;
      }
    }
  }

  public static GrString replaceStringInjectionByLiteral(GrStringInjection injection, GrLiteral literal) {
    GrString grString = (GrString)injection.getParent();

    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(grString.getProject());

    String literalText;

    //wrap last injection in inserted literal if it needed
    // e.g.: "bla bla ${foo}bla bla" and {foo} is replaced
    if (literal instanceof GrString) {
      final GrStringInjection[] injections = ((GrString)literal).getInjections();
      if (injections.length > 0) {
        GrStringInjection last = injections[injections.length - 1];
        if (last.getExpression() != null) {
          if (!checkBraceIsUnnecessary(last.getExpression(), injection.getNextSibling())) {
            wrapInjection(last);
          }
        }
      }
      literalText = removeQuotes(literal.getText());
    }
    else {
      final String text = removeQuotes(literal.getText());
      boolean escapeDoubleQuotes = !text.contains("\n") && grString.isPlainString();
      literalText = escapeSymbolsForGString(text, escapeDoubleQuotes, true);
    }

    if (literalText.contains("\n")) {
      wrapGStringInto(grString, TRIPLE_DOUBLE_QUOTES);
    }
    
    final GrExpression expression = factory.createExpressionFromText("\"\"\"${}" + literalText + "\"\"\"");

    expression.getFirstChild().delete();//quote
    expression.getFirstChild().delete();//empty gstring content
    expression.getFirstChild().delete();//empty injection

    final ASTNode node = grString.getNode();
    if (expression.getFirstChild() != null) {
      if (expression.getFirstChild() == expression.getLastChild()) {
        node.replaceChild(injection.getNode(), expression.getFirstChild().getNode());
      }
      else {
        node.addChildren(expression.getFirstChild().getNode(), expression.getLastChild().getNode(), injection.getNode());
        node.removeChild(injection.getNode());
      }
    }
    return grString;
  }

  private static void wrapGStringInto(GrString grString, String quotes) {
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(grString.getProject());
    final PsiElement firstChild = grString.getFirstChild();
    final PsiElement lastChild = grString.getLastChild();

    final GrExpression template = factory.createExpressionFromText(quotes + "$x" + quotes);
    if (firstChild != null &&
        firstChild.getNode().getElementType() == GroovyTokenTypes.mGSTRING_BEGIN &&
        !quotes.equals(firstChild.getText())) {
      grString.getNode().replaceChild(firstChild.getNode(), template.getFirstChild().getNode());
    }
    if (lastChild != null &&
        lastChild.getNode().getElementType() == GroovyTokenTypes.mGSTRING_END &&
        !quotes.equals(lastChild.getText())) {
      grString.getNode().replaceChild(lastChild.getNode(), template.getLastChild().getNode());
    }
  }

  public static void wrapInjection(GrStringInjection injection) {
    final GrExpression expression = injection.getExpression();
    LOG.assertTrue(expression != null);
    final GroovyPsiElementFactory instance = GroovyPsiElementFactory.getInstance(injection.getProject());
    final GrClosableBlock closure = instance.createClosureFromText("{foo}");
    closure.getNode().replaceChild(closure.getStatements()[0].getNode(), expression.getNode());
    injection.getNode().addChild(closure.getNode());
    CodeEditUtil.setNodeGeneratedRecursively(expression.getNode(), true);
  }

  public static boolean checkGStringInjectionForUnnecessaryBraces(GrStringInjection injection) {
    final GrClosableBlock block = injection.getClosableBlock();
    if (block == null) return false;

    final GrStatement[] statements = block.getStatements();
    if (statements.length != 1) return false;

    if (!(statements[0] instanceof GrReferenceExpression)) return false;

    return checkBraceIsUnnecessary(statements[0], injection.getNextSibling());
  }

  private static boolean checkBraceIsUnnecessary(GrStatement injected, PsiElement next) {
    if (next.getTextLength() == 0) next = next.getNextSibling();

    char nextChar = next.getText().charAt(0);
    if (nextChar == '"' || nextChar == '$') {
      return true;
    }
    final GroovyPsiElementFactory elementFactory = GroovyPsiElementFactory.getInstance(injected.getProject());
    final GrExpression gString;
    try {
      gString = elementFactory.createExpressionFromText("\"$" + injected.getText() + next.getText() + '"');
    }
    catch (Exception e) {
      return false;
    }
    if (!(gString instanceof GrString)) return false;

    PsiElement child = gString.getFirstChild();
    if (!(child.getNode().getElementType() == GroovyTokenTypes.mGSTRING_BEGIN)) return false;

    child = child.getNextSibling();
    if (!(child instanceof GrStringContent)) return false;

    child = child.getNextSibling();
    if (!(child instanceof GrStringInjection)) return false;

    final PsiElement refExprCopy = ((GrStringInjection)child).getExpression();
    if (!(refExprCopy instanceof GrReferenceExpression)) return false;

    final GrReferenceExpression refExpr = (GrReferenceExpression)injected;
    return PsiUtil.checkPsiElementsAreEqual(refExpr, refExprCopy);
  }

  public static void removeUnnecessaryBracesInGString(GrString grString) {
    for (GrStringInjection child : grString.getInjections()) {
      if (checkGStringInjectionForUnnecessaryBraces(child)) {
        final GrClosableBlock closableBlock = child.getClosableBlock();
        final GrReferenceExpression refExpr = (GrReferenceExpression)closableBlock.getStatements()[0];
        final GrReferenceExpression copy = (GrReferenceExpression)refExpr.copy();
        final ASTNode oldNode = closableBlock.getNode();
        oldNode.getTreeParent().replaceChild(oldNode, copy.getNode());
      }
    }
  }

  public static String getStartQuote(String text) {
    if (text.startsWith(TRIPLE_QUOTES)) return TRIPLE_QUOTES;
    if (text.startsWith(QUOTE)) return QUOTE;
    if (text.startsWith(TRIPLE_DOUBLE_QUOTES)) return TRIPLE_DOUBLE_QUOTES;
    if (text.startsWith(DOUBLE_QUOTES)) return DOUBLE_QUOTES;
    if (text.startsWith(SLASH)) return SLASH;
    if (text.startsWith(DOLLAR_SLASH)) return DOLLAR_SLASH;
    return "";
  }

  public static String getEndQuote(String text) {
    if (text.endsWith(TRIPLE_QUOTES)) return TRIPLE_QUOTES;
    if (text.endsWith(QUOTE)) return QUOTE;
    if (text.endsWith(TRIPLE_DOUBLE_QUOTES)) return TRIPLE_DOUBLE_QUOTES;
    if (text.endsWith(DOUBLE_QUOTES)) return DOUBLE_QUOTES;
    if (text.endsWith(SLASH)) return SLASH;
    if (text.endsWith(SLASH_DOLLAR)) return SLASH_DOLLAR;
    return "";
  }

  @Nullable
  @Contract("null -> null")
  public static TextRange getStringContentRange(@Nullable PsiElement element) {
    if (element == null) return null;
    IElementType elementType = element.getNode().getElementType();
    if (elementType != GroovyTokenTypes.mSTRING_LITERAL && elementType != GroovyTokenTypes.mGSTRING_LITERAL) return null;

    String text = element.getText();
    String startQuote = getStartQuote(text);
    String endQuote = getEndQuote(text.substring(startQuote.length()));
    return new TextRange(startQuote.length(), element.getTextLength() - endQuote.length());
  }


  public static boolean parseRegexCharacters(@NotNull String chars,
                                             @NotNull StringBuilder outChars,
                                             @Nullable int[] sourceOffsets,
                                             boolean escapeSlash) {
    assert sourceOffsets == null || sourceOffsets.length == chars.length() + 1;
    if (chars.indexOf('\\') < 0) {
      outChars.append(chars);
      if (sourceOffsets != null) {
        for (int i = 0; i < sourceOffsets.length; i++) {
          sourceOffsets[i] = i;
        }
      }
      return true;
    }

    int index = 0;
    final int outOffset = outChars.length();
    while (index < chars.length()) {
      char c = chars.charAt(index++);
      if (sourceOffsets != null) {
        sourceOffsets[outChars.length() - outOffset] = index - 1;
        sourceOffsets[outChars.length() + 1 - outOffset] = index;
      }
      if (c != '\\') {
        outChars.append(c);
        continue;
      }
      if (index == chars.length()) {
        outChars.append('\\');
        return true;
      }
      c = chars.charAt(index++);
      switch (c) {
        case '/':
          if (escapeSlash) {
            outChars.append(c);
            if (sourceOffsets != null) {
              sourceOffsets[outChars.length() - outOffset] = index;
            }
          }
          else {
            outChars.append('\\').append('/');
          }

          break;
        case '\n':
          //do nothing
          if (sourceOffsets != null) {
            sourceOffsets[outChars.length() - outOffset] = index;
          }
          break;
        case 'u':
          // uuuuu1234 is valid too
          while (index != chars.length() && chars.charAt(index) == 'u') {
            index++;
          }
          if (index + 4 <= chars.length()) {
            try {
              int code = Integer.parseInt(chars.substring(index, index + 4), 16);
              c = chars.charAt(index);
              if (c == '+' || c == '-') return false;
              outChars.append((char)code);
              index += 4;

              if (sourceOffsets != null) {
                sourceOffsets[outChars.length() - outOffset] = index;
              }
            }
            catch (Exception e) {
              return false;
            }
          }
          else {
            return false;
          }
          break;
        default:
          outChars.append('\\').append(c);
          if (sourceOffsets != null) {
            sourceOffsets[outChars.length() - outOffset] = index;
          }

      }
    }
    return true;
  }

  /**
   * @see com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl#parseStringCharacters(String, StringBuilder, int[])
   */
  public static boolean parseStringCharacters(@NotNull String chars, @NotNull StringBuilder outChars, @Nullable int[] sourceOffsets) {
    assert sourceOffsets == null || sourceOffsets.length == chars.length()+1;
    if (chars.indexOf('\\') < 0) {
      outChars.append(chars);
      if (sourceOffsets != null) {
        for (int i = 0; i < sourceOffsets.length; i++) {
          sourceOffsets[i] = i;
        }
      }
      return true;
    }
    int index = 0;
    final int outOffset = outChars.length();
    while (index < chars.length()) {
      char c = chars.charAt(index++);
      if (sourceOffsets != null) {
        sourceOffsets[outChars.length()-outOffset] = index - 1;
        sourceOffsets[outChars.length() + 1 -outOffset] = index;
      }
      if (c != '\\') {
        outChars.append(c);
        continue;
      }
      if (index == chars.length()) return false;
      c = chars.charAt(index++);
      switch (c) {
        case'b':
          outChars.append('\b');
          break;
        case't':
          outChars.append('\t');
          break;
        case'n':
          outChars.append('\n');
          break;
        case'f':
          outChars.append('\f');
          break;
        case'r':
          outChars.append('\r');
          break;
        case'"':
          outChars.append('\"');
          break;
        case'\'':
          outChars.append('\'');
          break;
        case'$':
          outChars.append('$');
          break;
        case'\\':
          outChars.append('\\');
          break;
        case '\n':
          //do nothing
          break;

        case'0':
        case'1':
        case'2':
        case'3':
        case'4':
        case'5':
        case'6':
        case'7':
          char startC = c;
          int v = (int)c - '0';
          if (index < chars.length()) {
            c = chars.charAt(index++);
            if ('0' <= c && c <= '7') {
              v <<= 3;
              v += c - '0';
              if (startC <= '3' && index < chars.length()) {
                c = chars.charAt(index++);
                if ('0' <= c && c <= '7') {
                  v <<= 3;
                  v += c - '0';
                }
                else {
                  index--;
                }
              }
            }
            else {
              index--;
            }
          }
          outChars.append((char)v);
          break;

        case'u':
          // uuuuu1234 is valid too
          while (index != chars.length() && chars.charAt(index) == 'u') {
            index++;
          }
          if (index + 4 <= chars.length()) {
            try {
              int code = Integer.parseInt(chars.substring(index, index + 4), 16);
              //line separators are invalid here
              if (code == 0x000a || code == 0x000d) return false;
              c = chars.charAt(index);
              if (c == '+' || c == '-') return false;
              outChars.append((char)code);
              index += 4;
            }
            catch (Exception e) {
              return false;
            }
          }
          else {
            return false;
          }
          break;
        default:
          return false;
      }
      if (sourceOffsets != null) {
        sourceOffsets[outChars.length()-outOffset] = index;
      }
    }
    return true;
  }
  
  public static GrLiteral createStringFromRegex(@NotNull GrLiteral regex) {
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(regex.getProject());


    if (regex instanceof GrRegex) {
      StringBuilder builder = new StringBuilder();
      String quote = regex.getText().indexOf('\n') >= 0 ? TRIPLE_DOUBLE_QUOTES : DOUBLE_QUOTES;
      builder.append(quote);
      for (PsiElement child = regex.getFirstChild(); child!=null; child = child.getNextSibling()) {
        final IElementType type = child.getNode().getElementType();
        if (type == GroovyTokenTypes.mREGEX_CONTENT || type == GroovyElementTypes.GSTRING_CONTENT) {
          builder.append(escapeSymbolsForGString(unescapeSlashyString(child.getText()), quote.equals(DOUBLE_QUOTES), true));
        }
        else if (type == GroovyTokenTypes.mDOLLAR_SLASH_REGEX_CONTENT) {
          builder.append(escapeSymbolsForGString(unescapeDollarSlashyString(child.getText()), quote.equals(DOUBLE_QUOTES), true));
        }
        else if (type == GroovyElementTypes.GSTRING_INJECTION) {
          builder.append(child.getText());
        }
      }
      builder.append(quote);
      return (GrLiteral)factory.createExpressionFromText(builder.toString());
    }
    else {
      Object value = regex.getValue();
      LOG.assertTrue(value==null || value instanceof String);
      if (value == null) {
        value = removeQuotes(regex.getText());
      }
      return factory.createLiteralFromValue(value);
    }
  }

  public static boolean isWellEndedString(PsiElement element) {
    final String text = element.getText();

    if (!(text.endsWith("'") ||
          text.endsWith("\"") ||
          text.endsWith("'''") ||
          text.endsWith("\"\"\"") ||
          text.endsWith("/") ||
          text.endsWith("/$"))) {
      return false;
    }


    final IElementType type = element.getNode().getElementType();
    if (TokenSets.STRING_LITERAL_SET.contains(type)) return true;

    final PsiElement lastChild = element.getLastChild();
    if (lastChild == null) return false;

    final IElementType lastType = lastChild.getNode().getElementType();
    if (type == GroovyElementTypes.GSTRING) return lastType == GroovyTokenTypes.mGSTRING_END;
    if (type == GroovyElementTypes.REGEX) return lastType == GroovyTokenTypes.mREGEX_END || lastType ==
                                                                                            GroovyTokenTypes.mDOLLAR_SLASH_REGEX_END;

    return false;
  }

  public static void fixAllTripleQuotes(StringBuilder builder, int position) {
    for (int i = builder.indexOf("'''", position); i >= 0; i = builder.indexOf("'''", i)) {
      builder.replace(i + 2, i + 3, "\\'");
    }
  }

  public static void fixAllTripleDoubleQuotes(StringBuilder builder, int position) {
    for (int i = builder.indexOf("\"\"\"", position); i >= 0; i = builder.indexOf("\"\"\"", i)) {
      builder.replace(i + 2, i + 3, "\\\"");
    }
  }

  public static boolean isPlainStringLiteral(ASTNode node) {
    String text = node.getText();
    return text.length() < 3 && text.equals("''") || text.length() >= 3 && !text.startsWith("'''");
  }

  public static boolean isMultilineStringLiteral(GrLiteral literal) {
    String quote = getStartQuote(literal.getText());
    return TRIPLE_QUOTES.equals(quote) || TRIPLE_DOUBLE_QUOTES.equals(quote) || SLASH.equals(quote) || DOLLAR_SLASH.equals(quote);
  }

  public static boolean isSinglelineStringLiteral(GrLiteral literal) {
    String quote = getStartQuote(literal.getText());
    return QUOTE.equals(quote) || DOUBLE_QUOTES.equals(quote);
  }

  public static StringBuilder getLiteralTextByValue(String value) {
    StringBuilder buffer = new StringBuilder();
    if (value.indexOf('\n') >= 0) {
      buffer.append("'''");
      escapeStringCharacters(value.length(), value, "", false, true, buffer);
      buffer.append("'''");
    }
    else {
      buffer.append("'");
      escapeStringCharacters(value.length(), value, "'", false, true, buffer);
      buffer.append("'");
    }
    return buffer;
  }

  public static PsiElement findContainingLiteral(PsiElement token) {

    PsiElement parent = token.getParent();
    if (parent instanceof GrStringContent) parent = parent.getParent();

    return parent;
  }

  /**
   * Checks whether a literal is a string literal of any kind
   */
  public static boolean isStringLiteral(GrLiteral literal) {
    if (literal instanceof GrString) return true;

    if (literal instanceof GrLiteralImpl) {
      IElementType type = GrLiteralImpl.getLiteralType(literal);
      return TokenSets.STRING_LITERAL_SET.contains(type);
    }

    return false;
  }

  public static boolean isRegex(GrLiteral literal) {
    if (literal instanceof GrRegex) return true;

    String quote = getStartQuote(literal.getText());
    return SLASH.equals(quote) || DOLLAR_SLASH.equals(quote);
  }

  public static boolean isSlashyString(GrLiteral literal) {
    return SLASH.equals(getStartQuote(literal.getText()));
  }

  public static boolean isDollarSlashyString(GrLiteral literal) {
    return DOLLAR_SLASH.equals(getStartQuote(literal.getText()));
  }

  public static boolean isSingleQuoteString(GrLiteral literal) {
    return QUOTE.equals(getStartQuote(literal.getText()));
  }

  public static boolean isDoubleQuoteString(GrLiteral literal) {
    return DOUBLE_QUOTES.equals(getStartQuote(literal.getText()));
  }

  public static boolean isTripleQuoteString(GrLiteral literal) {
    return TRIPLE_QUOTES.equals(getStartQuote(literal.getText()));
  }

  public static boolean isTripleDoubleQuoteString(GrLiteral literal) {
    return TRIPLE_DOUBLE_QUOTES.equals(getStartQuote(literal.getText()));
  }
}
