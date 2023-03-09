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
    return EP.allForLanguageOrAny(problem.getText().getCommonParent().getLanguage()).stream().filter(f -> f.shouldIgnore(problem));
  }

  /** @return whether the given problem should not be reported by grammar checking highlighting */
  public abstract boolean shouldIgnore(@NotNull TextProblem problem);
}
