/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.introduce;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
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

  @Nullable
  public static StringPartInfo findStringPart(@NotNull PsiFile file, int startOffset, int endOffset) {
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

  public StringPartInfo(@NotNull GrLiteral literal, @NotNull final TextRange range) {
    myLiteral = literal;

    if (literal instanceof GrString) {
      final GrStringInjection[] injections = ((GrString)literal).getInjections();
      myInjections = ContainerUtil.filter(injections, new Condition<GrStringInjection>() {
        @Override
        public boolean value(GrStringInjection injection) {
          return range.contains(injection.getTextRange());
        }
      });
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

  @Nullable
  private static GrLiteral findLiteral(@NotNull PsiElement psi) {
    if (isStringLiteral(psi.getParent())) {
      return (GrLiteral)psi.getParent();
    }

    if (isStringLiteral(psi.getParent().getParent())) {
      return (GrLiteral)psi.getParent().getParent();
    }

    if (psi instanceof GrString) {
      return (GrLiteral)psi;
    }

    return null;
  }

  private static boolean isStringLiteral(final PsiElement psi) {
    return psi instanceof GrLiteral && TokenSets.STRING_LITERAL_SET.contains(GrLiteralImpl.getLiteralType((GrLiteral)psi)) || psi instanceof GrString;
  }

  @NotNull
  public GrExpression replaceLiteralWithConcatenation(@Nullable String varName) {

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
        return ((GrBinaryExpression)((GrBinaryExpression)replaced).getLeftOperand()).getRightOperand();
      }
      if (!prefixExists && suffixExists) {
        return ((GrBinaryExpression)replaced).getLeftOperand();
      }
      if (prefixExists && !suffixExists) {
        return ((GrBinaryExpression)replaced).getRightOperand();
      }
      if (!prefixExists && !suffixExists) {
        return replaced;
      }
    }
    catch (ClassCastException c) {
      throw new IncorrectOperationException(buffer.toString());
    }

    throw new IncorrectOperationException(buffer.toString());
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

  @NotNull
  private static String prepareGString(@NotNull String content) {
    StringBuilder buffer = new StringBuilder();
    boolean multiline = content.contains("\n");
    buffer.append(multiline ? GrStringUtil.TRIPLE_DOUBLE_QUOTES : GrStringUtil.DOUBLE_QUOTES);
    GrStringUtil.escapeSymbolsForGString(content, multiline, false, buffer);
    buffer.append(multiline ? GrStringUtil.TRIPLE_DOUBLE_QUOTES : GrStringUtil.DOUBLE_QUOTES);

    return buffer.toString();
  }

  @NotNull
  public GrLiteral getLiteral() {
    return myLiteral;
  }

  @NotNull
  public TextRange getRange() {
    return myRange;
  }

  @NotNull
  public List<GrStringInjection> getInjections() {
    return myInjections;
  }

  @NotNull
  public GrLiteral createLiteralFromSelected() {
    return (GrLiteral)GroovyPsiElementFactory.getInstance(myLiteral.getProject()).createExpressionFromText(prepareSelected());
  }
}
