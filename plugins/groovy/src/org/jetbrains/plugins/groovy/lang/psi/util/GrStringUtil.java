package org.jetbrains.plugins.groovy.lang.psi.util;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;

/**
 * @author Maxim.Medvedev
 */
public class GrStringUtil {
  private static final String TRIPLE_QUOTES = "'''";
  private static final String QUOTE = "'";
  private static final String DOUBLE_QUOTES = "\"";
  private static final String TRIPLE_DOUBLE_QUOTES = "\"\"\"";

  private GrStringUtil() {
  }

  public static String escapeSymbolsForGString(String s, boolean escapeDoubleQuotes) {
    return escapeSymbolsForGString(s, escapeDoubleQuotes, false);
  }

  public static String escapeSymbolsForGString(String s, boolean escapeDoubleQuotes, boolean forInjection) {
    StringBuilder b = new StringBuilder();
    escapeStringCharacters(s.length(), s, escapeDoubleQuotes ? "$\"" : "$", false, forInjection, b);
    if (!forInjection) {
      unescapeCharacters(b, escapeDoubleQuotes ? "'" : "'\"", true);
    }
    return b.toString();
  }

  public static String escapeSymbolsForString(String s, boolean escapeQuotes) {
    return escapeSymbolsForString(s, escapeQuotes, false);
  }

  public static String escapeSymbolsForString(String s, boolean escapeQuotes, boolean forInjection) {
    final StringBuilder builder = new StringBuilder();
    escapeStringCharacters(s.length(), s, escapeQuotes ? "'" : "", false, forInjection, builder);
    if (!forInjection) {
      unescapeCharacters(builder, escapeQuotes ? "$\"" : "$'\"", true);
    }
    return builder.toString();
  }

  @NotNull
  public static StringBuilder escapeStringCharacters(int length,
                                                     @NotNull String str,
                                                     @Nullable String additionalChars,
                                                     boolean escapeNR,
                                                     boolean escapeSlash,
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
          if (escapeSlash) {
            buffer.append("\\\\");
          }
          else {
            buffer.append("\\");
          }
          break;

        case '\n':
          if (escapeNR) {
            buffer.append("\\n");
          }
          else {
            buffer.append('\n');
          }
          break;

        case '\r':
          if (escapeNR) {
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
            String hexCode = Integer.toHexString(ch).toUpperCase();
            buffer.append("\\u");
            int paddingCount = 4 - hexCode.length();
            while (paddingCount-- > 0) {
              buffer.append(0);
            }
            buffer.append(hexCode);
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

  public static String escapeSymbols(String s, String toEscape) {
    StringBuilder builder = new StringBuilder();
    boolean escaped = false;
    for (int i = 0; i < s.length(); i++) {
      final char ch = s.charAt(i);
      if (escaped) {
        builder.append(ch);
        escaped = false;
        continue;
      }
      if (ch == '\\') {
        escaped = true;
      }
      if (toEscape.indexOf(ch) >= 0) {
        builder.append('\\');
      }

      builder.append(ch);
    }
    return builder.toString();
  }

  public static String removeQuotes(@NotNull String s) {
    if (s.startsWith(TRIPLE_QUOTES) || s.startsWith(TRIPLE_DOUBLE_QUOTES)) {
      if (s.endsWith(s.substring(0, 3))) {
        return s.substring(3, s.length() - 3);
      }
      else {
        return s.substring(3);
      }
    }
    else if (s.startsWith(QUOTE) || s.startsWith(DOUBLE_QUOTES)) {
      if (s.length() >= 2 && s.endsWith(s.substring(0, 1))) {
        return s.substring(1, s.length() - 1);
      }
      else {
        return s.substring(1);
      }
    }
    return s;
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

  public static boolean isReplacedExpressionInGStringInjection(GrExpression replacedExpression) {
    PsiElement parent = replacedExpression.getParent();
    if (parent instanceof GrClosableBlock) {
      parent = parent.getParent();
    }
    return parent instanceof GrStringInjection;
  }

  /**
   * @param injection - expected that injection must have GrString parent
   * @param literal
   * @return
   */
  //todo use this code
  public static GrString replaceStringInjectionByLiteral(PsiElement injection, GrLiteral literal) {
    if (injection.getParent() instanceof GrClosableBlock) {
      injection = injection.getParent();
    }
    injection = injection.getParent();
    GrString grString = (GrString)injection.getParent();

    int injectionNumber = -1;
    for (PsiElement child : grString.getChildren()) {
      injectionNumber++;
      if (child == injection) {
        break;
      }
    }
    if (injectionNumber == -1) return grString;

    GrString grStringWithBraces = addAllBracesInGString(grString);
//    injection = grStringWithBraces.getChildren()[injectionNumber];

    if (literal instanceof GrString) {
      literal = addAllBracesInGString((GrString)literal);
    }

    String literalText = escapeSymbolsForGString(removeQuotes(literal.getText()), false);
    if(literalText.contains("\n")) literalText=escapeSymbolsForGString(literalText, true);

    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(grString.getProject());
    final GrExpression expression = factory.createExpressionFromText("\"${}" + literalText + "\"");

    expression.getFirstChild().delete();
    expression.getFirstChild().delete();
    expression.getLastChild().delete();

    final ASTNode node = grString.getNode();
    if (expression.getFirstChild() != null) {
      if (expression.getFirstChild() == expression.getLastChild()) {
        node.replaceChild(injection.getNode(), expression.getFirstChild().getNode());
      }
      else {
        node.addChildren(expression.getFirstChild().getNode(), expression.getLastChild().getNode(), injection.getNode());
        injection.delete();
      }
    }
    return grString;
  }

  public static GrString addAllBracesInGString(GrString grString) {
    StringBuilder newString = new StringBuilder();

    for (PsiElement child = grString.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof GrStringInjection && ((GrStringInjection)child).getExpression() != null) {
        final GrExpression refExpr = ((GrStringInjection)child).getExpression();
        newString.append("${").append(refExpr != null ? refExpr.getText() : "").append('}');
      }
      else {
        newString.append(child.getText());
      }
    }
    return (GrString)GroovyPsiElementFactory.getInstance(grString.getProject()).createExpressionFromText(newString.toString());
  }

  public static boolean checkGStringInjectionForUnnecessaryBraces(PsiElement element) {
    if (!(element instanceof GrStringInjection)) return false;
    GrStringInjection injection = (GrStringInjection)element;
    final GrClosableBlock block = injection.getClosableBlock();
    if (block == null) return false;

    final GrStatement[] statements = block.getStatements();
    if (statements.length != 1) return false;

    if (!(statements[0] instanceof GrReferenceExpression)) return false;

    final PsiElement next = injection.getNextSibling();
    if (!(next instanceof LeafPsiElement)) return false;

    char nextChar = next.getText().charAt(0);
    if (nextChar == '"' || nextChar == '$') {
      return true;
    }
    final GroovyPsiElementFactory elementFactory = GroovyPsiElementFactory.getInstance(element.getProject());
    final GrExpression gString;
    try {
      gString = elementFactory.createExpressionFromText("\"$" + statements[0].getText() + nextChar + '"');
    }
    catch (Exception e) {
      return false;
    }
    if (!(gString instanceof GrString)) return false;

    final PsiElement child = gString.getChildren()[0];
    if (!(child instanceof GrStringInjection)) return false;

    final PsiElement refExprCopy = ((GrStringInjection)child).getExpression();
    if (!(refExprCopy instanceof GrReferenceExpression)) return false;

    final GrReferenceExpression refExpr = (GrReferenceExpression)statements[0];
    return Comparing.equal(refExpr.getName(), ((GrReferenceExpression)refExprCopy).getName());
  }

  public static void removeUnnecessaryBracesInGString(GrString grString) {
    for (PsiElement child : grString.getChildren()) {
      if (checkGStringInjectionForUnnecessaryBraces(child)) {
        final GrClosableBlock closableBlock = ((GrStringInjection)child).getClosableBlock();
        final GrReferenceExpression refExpr = (GrReferenceExpression)closableBlock.getStatements()[0];
        final GrReferenceExpression copy = (GrReferenceExpression)refExpr.copy();
        final ASTNode oldNode = closableBlock.getNode();
        oldNode.getTreeParent().replaceChild(oldNode, copy.getNode());
      }
    }
  }

  public static boolean isPlainString(@NotNull GrLiteral literal) {
    return literal.getText().startsWith("'");
  }

  public static String getStartQuote(String text) {
    if (text.startsWith(TRIPLE_QUOTES)) return TRIPLE_QUOTES;
    if (text.startsWith(QUOTE)) return QUOTE;
    if (text.startsWith(TRIPLE_DOUBLE_QUOTES)) return TRIPLE_DOUBLE_QUOTES;
    if (text.startsWith(DOUBLE_QUOTES)) return DOUBLE_QUOTES;
    return "";
  }
}
