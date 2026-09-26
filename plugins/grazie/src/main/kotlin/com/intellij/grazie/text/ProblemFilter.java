package com.intellij.grazie.text;

import com.intellij.lang.LanguageExtension;
import org.jetbrains.annotations.NotNull;

import java.util.stream.Stream;

/**
 * An extension allowing to prevent some text problems from being reported,
 * registered in {@code plugin.xml} under {@code "com.intellij.grazie.problemFilter"} qualified name.
 * @see com.intellij.grazie.utils.ProblemFilterUtil
 */
public abstract class ProblemFilter {
  private static final LanguageExtension<ProblemFilter> EP = new LanguageExtension<>("com.intellij.grazie.problemFilter");

  public static Stream<ProblemFilter> allIgnoringFilters(TextProblem problem) {
    Stream<ProblemFilter> filters = EP.allForLanguageOrAny(problem.getText().getCommonParent().getLanguage()).stream();
    return problem.isSpellingProblem() ?
           filters.filter(f -> f.shouldIgnoreTypo(problem)) :
           filters.filter(f -> f.shouldIgnore(problem));
  }

  public static Stream<ProblemFilter> allIgnoringFilters(TextContent content) {
    return EP.allForLanguageOrAny(content.getCommonParent().getLanguage()).stream().filter(f -> f.shouldIgnore(content));
  }

  /** @return whether the given grammar- or style-problem should not be reported by proofreading checking highlighting */
  public abstract boolean shouldIgnore(@NotNull TextProblem problem);

  /** @return whether the given spelling problem should not be reported by proofreading checking highlighting */
  public boolean shouldIgnoreTypo(@NotNull TextProblem problem) { return false; }

  /** @return whether the given content should be skipped entirely by proofreading (spell-, grammar- and style-checking) */
  public boolean shouldIgnore(@NotNull TextContent content) { return false; }
}
