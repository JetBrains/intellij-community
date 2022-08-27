// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch.impl.matcher.predicates;

import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.MalformedPatternException;
import com.intellij.structuralsearch.MatchUtil;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.StructuralSearchUtil;
import com.intellij.structuralsearch.impl.matcher.MatchContext;
import com.intellij.structuralsearch.impl.matcher.MatchResultImpl;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class RegExpPredicate extends MatchPredicate {
  private Pattern pattern;
  private final String baseHandlerName;
  private boolean simpleString;
  private final boolean couldBeOptimized;
  @NotNull
  private final String regexp;
  private final boolean caseSensitive;
  private boolean multiline;
  private final boolean wholeWords;
  private final boolean target;
  private NodeTextGenerator myNodeTextGenerator;

  public interface NodeTextGenerator {
    String getText(PsiElement element);
  }

  public RegExpPredicate(@NotNull String regexp, boolean caseSensitive, String _baseHandlerName, boolean _wholeWords, boolean _target) {
    couldBeOptimized = !MatchUtil.containsRegExpMetaChar(regexp);
    if (!_wholeWords) {
      simpleString = couldBeOptimized;
    }

    this.regexp = regexp;
    this.caseSensitive = caseSensitive;
    this.wholeWords = _wholeWords;
    baseHandlerName = _baseHandlerName;

    if (!simpleString) {
      compilePattern();
    }
    target = _target;
  }

  private void compilePattern() {
    try {
      @NonNls String realRegexp = regexp;
      if (wholeWords) {
        realRegexp = ".*?\\b(?:" + realRegexp + ")\\b.*?";
      }

      pattern = Pattern.compile(realRegexp, (caseSensitive ? 0 : Pattern.CASE_INSENSITIVE) | (multiline ? Pattern.DOTALL : 0));
    }
    catch (PatternSyntaxException ex) {
      throw new MalformedPatternException(SSRBundle.message("error.incorrect.regexp.constraint", regexp, baseHandlerName));
    }
  }

  public boolean couldBeOptimized() {
    return couldBeOptimized;
  }

  @NotNull
  public String getRegExp() {
    return regexp;
  }

  /**
   * Attempts to match given handler node against given node.
   * @param matchedNode for matching
   * @param context of the matching
   * @return true if matching was successful and false otherwise
   */
  @Override
  public boolean match(@NotNull PsiElement matchedNode, int start, int end, @NotNull MatchContext context) {
    final String text = myNodeTextGenerator != null
                        ? myNodeTextGenerator.getText(matchedNode)
                        : StructuralSearchUtil.getMeaningfulText(matchedNode);

    boolean result = doMatch(text, start, end, context, matchedNode);
    if (!result) {
      matchedNode = StructuralSearchUtil.getParentIfIdentifier(matchedNode);
      final String alternativeText = StructuralSearchUtil.getAlternativeText(matchedNode, text);
      if (alternativeText != null) {
        result = doMatch(alternativeText, start, end, context, matchedNode);
      }
    }

    return result;
  }

  public boolean doMatch(@NotNull String text, @NotNull MatchContext context, @NotNull PsiElement matchedElement) {
    return doMatch(text, 0, -1 ,context, matchedElement);
  }

  private boolean doMatch(@NotNull String text, int from, int end, @NotNull MatchContext context, @NotNull PsiElement matchedElement) {
    if (from > 0 || end != -1) {
      text = text.substring(from, (end == -1 || end >= text.length()) ? text.length() : end);
    }

    if (simpleString) {
      return caseSensitive ? text.equals(regexp) : text.equalsIgnoreCase(regexp);
    }

    if(!multiline && text.contains("\n")) setMultiline(true);
    final Matcher matcher = pattern.matcher(text);

    if (!matcher.matches()) {
      return false;
    }
    for (int i = 1; i <= matcher.groupCount(); i++) {
      context.getResult().addChild(
        new MatchResultImpl(
          baseHandlerName + "_" + i,
          matcher.group(i),
          matchedElement,
          matcher.start(i),
          matcher.end(i),
          target
        )
      );
    }
    return true;
  }


  public void setNodeTextGenerator(NodeTextGenerator nodeTextGenerator) {
    myNodeTextGenerator = nodeTextGenerator;
  }

  public void setMultiline(boolean b) {
    multiline = b;
    compilePattern();
  }

  public boolean isWholeWords() {
    return wholeWords;
  }
}
