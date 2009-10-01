package org.jetbrains.plugins.groovy.intentions.conversions;

import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.IntentionUtils;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

import java.util.ArrayList;

public class ConvertGStringToStringIntention extends Intention {

  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new ConvertibleGStringLiteralPredicate();
  }

  public void processIntention(@NotNull PsiElement element) throws IncorrectOperationException {
    final GrLiteral exp = (GrLiteral)element;
    IntentionUtils.replaceExpression(convertGStringLiteralToStringLiteral(exp), exp);
  }

  private static String convertGStringLiteralToStringLiteral(GrLiteral literal) {
    PsiElement child = literal.getFirstChild();
    if (child == null) return literal.getText();
    String text;

    ArrayList<String> list = new ArrayList<String>();

    PsiElement prevSibling = null;
    PsiElement nextSibling;
    do {
      text = child.getText();
      nextSibling = child.getNextSibling();
      if (child instanceof GrClosableBlock) {
        text = prepareClosableBlock((GrClosableBlock)child);
      }
      else if (child instanceof GrReferenceExpression) {
        text = prepareExpression((GrExpression)child);
      }
      else {
        text = prepareText(text, prevSibling == null, nextSibling == null,
                           nextSibling instanceof GrClosableBlock || nextSibling instanceof GrReferenceExpression);
      }
      if (text != null) {
        list.add(text);
      }
      prevSibling = child;
      child = child.getNextSibling();
    }
    while (child != null);

    StringBuilder builder = new StringBuilder(literal.getTextLength() * 2);

    if (list.size() == 0) return "''";

    builder.append(list.get(0));
    for (int i = 1; i < list.size(); i++) {
      builder.append(" + ").append(list.get(i));
    }
    return builder.toString();
  }

  private static String prepareClosableBlock(GrClosableBlock block) {
    final GrStatement statement = block.getStatements()[0];
    final GrExpression expr;
    if (statement instanceof GrReturnStatement) {
      expr = ((GrReturnStatement)statement).getReturnValue();
    }
    else {
      expr = (GrExpression)statement;
    }
    return prepareExpression(expr);

  }

  private static String prepareExpression(GrExpression expr) {
    String text = expr.getText();

    final PsiType type = expr.getType();
    if (type != null && CommonClassNames.JAVA_LANG_STRING.equals(type.getCanonicalText())) {
      if (expr instanceof GrBinaryExpression && GroovyTokenTypes.mPLUS.equals(((GrBinaryExpression)expr).getOperationTokenType())) {
        return '(' + text + ')';
      }
      else {
        return text;
      }
    }
    else {
      return "String.valueOf(" + text + ")";
    }
  }

  @Nullable
  private static String prepareText(String text, boolean isFirst, boolean isLast, boolean isBeforeInjection) {
    if (isFirst) {
      if (text.startsWith("\"\"\"")) {
        text = text.substring(3);
      }
      else if (text.startsWith("\"")) {
        text = text.substring(1);
      }
    }
    if (isLast) {
      if (text.endsWith("\"\"\"")) {
        text = text.substring(0, text.length() - 3);
      }
      else if (text.endsWith("\"")) {
        text = text.substring(0, text.length() - 1);
      }
    }
    if (isBeforeInjection) {
      text = text.substring(0, text.length() - 1);
    }
    if (text.length() == 0) return null;

    String escaped = escape(text);
    if (escaped.contains("\n")) {
      return "'''" + escaped + "'''";
    }
    else {
      return "'" + escaped + "'";
    }
  }

  private static String escape(String contents) {
    StringBuilder b = new StringBuilder(contents.length() * 2);
    final char[] chars = contents.toCharArray();
    final int len = chars.length - 1;
    int i;
    for (i = 0; i < len; i++) {
      if (chars[i] == '\\') {
        if (chars[i + 1] == '"' || chars[i + 1] == '$') {
          i++;
          b.append(chars[i]);
        }
        else if (chars[i + 1] == 'n') {
          b.append('\n');
          i++;
        }
        else {
          b.append('\\');
          i++;
          b.append(chars[i]);
        }
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
}
