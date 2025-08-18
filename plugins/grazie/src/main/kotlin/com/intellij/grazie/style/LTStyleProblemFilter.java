package com.intellij.grazie.style;

import com.intellij.grazie.grammar.LanguageToolChecker;
import com.intellij.grazie.grammar.LanguageToolRule;
import com.intellij.grazie.text.ProblemFilter;
import com.intellij.grazie.text.TextProblem;
import org.jetbrains.annotations.NotNull;

import static com.intellij.grazie.utils.CloudUtilsKt.isFunctionallyDisabled;

class LTStyleProblemFilter extends ProblemFilter {
  @Override
  public boolean shouldIgnore(@NotNull TextProblem problem) {
    if (isFunctionallyDisabled()) return false;
    return problem instanceof LanguageToolChecker.Problem && isStyleLike((LanguageToolChecker.Problem) problem);
  }

  static boolean isStyleLike(@NotNull LanguageToolChecker.Problem problem) {
    //noinspection KotlinInternalInJava
    return LanguageToolRule.isStyleLike(problem.getMatch().getRule());
  }
}
