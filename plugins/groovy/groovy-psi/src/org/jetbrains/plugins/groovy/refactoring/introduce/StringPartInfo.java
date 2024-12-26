// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.introduce;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.literals.GrLiteralImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * @author Max Medvedev
 */
public class StringPartInfo {
  private final GrLiteral myLiteral;
  private final TextRange myRange;
  private final List<GrStringInjection> myInjections;

  private final String myText;
  private final String myStartQuote;
  private final String myEndQuote;

  public static @Nullable StringPartInfo findStringPart(@NotNull PsiFile file, int startOffset, int endOffset) {
    final PsiElement start = file.findElementAt(startOffset);
    final PsiElement fin = file.findElementAt(endOffset - 1);
    if (start == null || fin == null) return null;

    final PsiElement psi = PsiTreeUtil.findCommonParent(start, fin);
    if (psi == null) return null;

    GrLiteral literal = findLiteral(psi);
    if (literal != null && checkSelectedRange(startOffset, endOffset, literal)) {
      return new StringPartInfo(literal, new TextRange(startOffset, endOffset));
    }

    return null;
  }

  public StringPartInfo(@NotNull GrLiteral literal, final @NotNull TextRange range) {
    myLiteral = literal;

    if (literal instanceof GrString) {
      final GrStringInjection[] injections = ((GrString)literal).getInjections();
      myInjections = ContainerUtil.filter(injections, injection -> range.contains(injection.getTextRange()));
    }
    else {
      myInjections = Collections.emptyList();
    }

    myText = myLiteral.getText();

    myStartQuote = GrStringUtil.getStartQuote(myText);
    myEndQuote = GrStringUtil.getEndQuote(myText);

    TextRange dataRange = new TextRange(myStartQuote.length(), myText.length() - myEndQuote.length());
    myRange = range.shiftRight(-literal.getTextRange().getStartOffset()).intersection(dataRange);
  }

  private static boolean checkSelectedRange(int startOffset, int endOffset, GrLiteral literal) {
    if (isWholeLiteralContentSelected(literal, startOffset, endOffset)) {
      return false;
    }

    if (literal instanceof GrString) {
      if (areInjectionsCut((GrString)literal, startOffset, endOffset)) {
        return false;
      }
    }

    if (isEscapesCut(literal, startOffset, endOffset)) {
      return false;
    }

    return true;
  }

  private static boolean isEscapesCut(GrLiteral literal, int startOffset, int endOffset) {
    String rawContent = GrStringUtil.removeQuotes(literal.getText());
    int[] offsets = new int[rawContent.length() + 1];

    if (GrStringUtil.isSingleQuoteString(literal) || GrStringUtil.isDoubleQuoteString(literal)) {
      GrStringUtil.parseStringCharacters(rawContent, new StringBuilder(), offsets);
    }
    else if (GrStringUtil.isSlashyString(literal)) {
      GrStringUtil.parseRegexCharacters(rawContent, new StringBuilder(), offsets, true);
    }
    else if (GrStringUtil.isDollarSlashyString(literal)) {
      GrStringUtil.parseRegexCharacters(rawContent, new StringBuilder(), offsets, false);
    }

    int contentStart = literal.getTextRange().getStartOffset() + GrStringUtil.getStartQuote(literal.getText()).length();

    int relativeStart = startOffset - contentStart;
    int relativeEnd = endOffset - contentStart;

    return ArrayUtil.find(offsets, relativeStart) < 0 ||
           ArrayUtil.find(offsets, relativeEnd) < 0;
  }

  public static boolean isWholeLiteralContentSelected(GrLiteral literal, int startOffset, int endOffset) {
    TextRange literalRange = literal.getTextRange();
    String literalText = literal.getText();
    String startQuote = GrStringUtil.getStartQuote(literalText);
    String endQuote = GrStringUtil.getEndQuote(literalText);

    return literalRange.getStartOffset()                    <= startOffset && startOffset <= literalRange.getStartOffset() + startQuote.length() &&
           literalRange.getEndOffset() - endQuote.length()  <= endOffset   && endOffset   <= literalRange.getEndOffset();
  }

  private static boolean areInjectionsCut(GrString literal, int startOffset, int endOffset) {
    TextRange selectionRange = new TextRange(startOffset, endOffset);

    GrStringInjection[] injections = literal.getInjections();
    for (GrStringInjection injection : injections) {
      TextRange range = injection.getTextRange();
      if (!selectionRange.contains(range) && !range.contains(selectionRange) && range.intersects(selectionRange)) {
        return true;
      }
    }
    return false;
  }

  private static @Nullable GrLiteral findLiteral(@NotNull PsiElement psi) {
    PsiElement parent = psi.getParent();
    if (isStringLiteral(parent)) {
      return (GrLiteral)parent;
    }

    if (parent == null) return null;

    PsiElement gParent = parent.getParent();
    if (isStringLiteral(gParent)) {
      return (GrLiteral)gParent;
    }

    if (psi instanceof GrString) {
      return (GrLiteral)psi;
    }

    return null;
  }

  @Contract("null -> false")
  private static boolean isStringLiteral(@Nullable PsiElement psi) {
    return psi instanceof GrLiteral && TokenSets.STRING_LITERAL_SET.contains(GrLiteralImpl.getLiteralType((GrLiteral)psi)) || psi instanceof GrString;
  }

  public @NotNull GrExpression replaceLiteralWithConcatenation(@Nullable String varName) {

    String prefix = preparePrefix();
    String suffix = prepareSuffix();

    StringBuilder buffer = new StringBuilder();
    boolean prefixExists = !GrStringUtil.removeQuotes(prefix).isEmpty();
    if (prefixExists) {
      buffer.append(prefix).append('+');
    }

    buffer.append(varName != null ? varName : prepareSelected());

    boolean suffixExists = !GrStringUtil.removeQuotes(suffix).isEmpty();
    if (suffixExists) {
      buffer.append('+').append(suffix);
    }

    final GrExpression concatenation = GroovyPsiElementFactory.getInstance(myLiteral.getProject()).createExpressionFromText(buffer);

    final GrExpression replaced = getLiteral().replaceWithExpression(concatenation, false);

    try {
      if (prefixExists && suffixExists) {
        return Objects.requireNonNull(((GrBinaryExpression)((GrBinaryExpression)replaced).getLeftOperand()).getRightOperand());
      }
      if (!prefixExists && suffixExists) {
        return ((GrBinaryExpression)replaced).getLeftOperand();
      }
      if (prefixExists) {
        return Objects.requireNonNull(((GrBinaryExpression)replaced).getRightOperand());
      }
      return replaced;
    }
    catch (ClassCastException c) {
      throw new IncorrectOperationException(buffer.toString());
    }
  }

  private String prepareSelected() {
    String content = myRange.substring(myLiteral.getText());
    return prepareLiteral(content);
  }

  private String prepareSuffix() {
    return myStartQuote + myText.substring(myRange.getEndOffset());
  }

  private String preparePrefix() {
    String prefix = myText.substring(0, myRange.getStartOffset());
    String content = GrStringUtil.removeQuotes(prefix);

    return prepareLiteral(content);
  }

  private String prepareLiteral(String content) {

    if (GrStringUtil.isSlashyString(myLiteral)) {
      if (content.endsWith("\\")) {
        String unescaped = GrStringUtil.unescapeSlashyString(content);
        return prepareGString(unescaped);
      }
    }
    else if (GrStringUtil.isDollarSlashyString(myLiteral)) {
      if (content.endsWith("$")) {
        String unescaped = GrStringUtil.unescapeDollarSlashyString(content);
        return prepareGString(unescaped);
      }
    }

    return myStartQuote + content + myEndQuote;
  }

  private static @NotNull String prepareGString(@NotNull String content) {
    StringBuilder buffer = new StringBuilder();
    boolean multiline = content.contains("\n");
    buffer.append(multiline ? GrStringUtil.TRIPLE_DOUBLE_QUOTES : GrStringUtil.DOUBLE_QUOTES);
    GrStringUtil.escapeSymbolsForGString(content, multiline, false, buffer);
    buffer.append(multiline ? GrStringUtil.TRIPLE_DOUBLE_QUOTES : GrStringUtil.DOUBLE_QUOTES);

    return buffer.toString();
  }

  public @NotNull GrLiteral getLiteral() {
    return myLiteral;
  }

  public @NotNull TextRange getRange() {
    return myRange;
  }

  public @NotNull List<GrStringInjection> getInjections() {
    return myInjections;
  }

  public @NotNull GrLiteral createLiteralFromSelected() {
    return (GrLiteral)GroovyPsiElementFactory.getInstance(myLiteral.getProject()).createExpressionFromText(prepareSelected());
  }
}
