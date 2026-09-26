package com.intellij.grazie.utils;

import com.intellij.grazie.text.RuleGroup;
import com.intellij.grazie.text.TextProblem;
import com.intellij.util.containers.ContainerUtil;

public final class ProblemFilterUtil {

  /**
   * Check if this problem reports a letter case issue at the very beginning of the text.
   * Such issues are often ignored in documentation tags.
   */
  public static boolean isInitialCasingIssue(TextProblem problem) {
    return ContainerUtil.exists(problem.getHighlightRanges(), r -> r.getStartOffset() == 0) && problem.fitsGroup(RuleGroup.CASING);
  }

  /**
   * Check if this problem reports a sentence capitalization or missing trailing punctuation issue in a single-sentence text fragment.
   * Such issues are often ignored in documentation tags or comments.
   */
  public static boolean isUndecoratedSingleSentenceIssue(TextProblem problem) {
    return Text.isSingleSentence(problem.getText()) && problem.fitsGroup(RuleGroup.UNDECORATED_SINGLE_SENTENCE);
  }
}
