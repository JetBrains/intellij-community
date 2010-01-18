package org.jetbrains.plugins.groovy.lang.psi.util;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import org.jetbrains.annotations.NotNull;
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
  public static final String GROOVY_LANG_GSTRING = "groovy.lang.GString";

  private GrStringUtil() {
  }

  public static String escapeSymbolsForGString(String s, boolean escapeDoubleQuotes) {
    StringBuilder b = new StringBuilder();
    final char[] chars = s.toCharArray();
    final int len = chars.length - 1;
    int i;
    for (i = 0; i < len; i++) {
      if (chars[i] == '\\') {
        final char next = chars[i + 1];
        if (next == '\'') {
          b.append('\'');
          i++;
        }
        else if (next == 'n') {
          b.append('\n');
          i++;
        }
        else if (escapeDoubleQuotes && next == '"') {
          b.append('"');
          i++;
        }
        else {
          b.append(chars[i]);
          i++;
          b.append(chars[i]);
        }
        continue;
      }
      if (chars[i] == '"' || chars[i] == '$') b.append('\\');
      b.append(chars[i]);
    }
    if (i == len) {
      if (chars[i] == '"') b.append('\\');
      b.append(chars[i]);
    }
    return b.toString();
  }

  public static String escapeSymbolsForString(String s, boolean escapeQuotes) {
    StringBuilder b = new StringBuilder();
    final char[] chars = s.toCharArray();
    final int len = chars.length - 1;
    int i;
    for (i = 0; i < len; i++) {
      if (chars[i] == '\\') {
        final char next = chars[i + 1];
        if (next == '"' || next == '$') {
          b.append(next);
        }
        else if (next == 'n') {
          b.append('\n');
        }
        else if (escapeQuotes && next == '\'') {
          b.append(next);
        }
        else {
          b.append('\\');
          b.append(next);
        }
        i++;
        continue;
      }
      if (chars[i] == '\'') b.append('\\');
      b.append(chars[i]);
    }

    if (i == len) {
      if (chars[i] == '\'') b.append('\\');
      b.append(chars[i]);
    }
    return b.toString();

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
      if (s.contains("\n")) {
        return TRIPLE_DOUBLE_QUOTES + s + TRIPLE_DOUBLE_QUOTES;
      }
      else {
        return DOUBLE_QUOTES + s + DOUBLE_QUOTES;
      }
    }
    else {
      if (s.contains("\n")) {
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
      if (child instanceof GrStringInjection && ((GrStringInjection)child).getReferenceExpression() != null) {
        final GrReferenceExpression refExpr = ((GrStringInjection)child).getReferenceExpression();
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

    final PsiElement refExprCopy = ((GrStringInjection)child).getReferenceExpression();
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
}
