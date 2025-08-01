package com.intellij.grazie.ide.language.xml;

import com.intellij.grazie.text.ProblemFilter;
import com.intellij.grazie.text.TextContent;
import com.intellij.grazie.text.TextProblem;
import com.intellij.openapi.util.text.Strings;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("SSBasedInspection")
final class XmlProblemFilter extends ProblemFilter {
  @Override
  public boolean shouldIgnore(@NotNull TextProblem problem) {
    if (!seemsNatural(problem.getText()) || isAfterPossibleEscape(problem)) return true;

    // This HTML (<b>text</b>) is currently split into 3 parts, and the bracket-pairing rule produces false positives.
    // This can be removed when we implement smarter tag content merging.
    String id = problem.getRule().getGlobalId();
    return id.startsWith("LanguageTool.") && id.endsWith("UNPAIRED_BRACKETS");
  }

  private static boolean seemsNatural(TextContent content) {
    return content.toString().contains(" ");
  }

  private static boolean isAfterPossibleEscape(@NotNull TextProblem problem) {
    TextContent text = problem.getText();
    return problem.getHighlightRanges().stream()
      .anyMatch(r -> Strings.contains(text, Math.max(0, r.getStartOffset() - 1), r.getEndOffset(), '\\'));
  }
}
