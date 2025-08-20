package com.intellij.grazie.style;

import com.intellij.grazie.grammar.LanguageToolChecker;
import com.intellij.grazie.text.ProblemFilter;
import com.intellij.grazie.text.TextProblem;
import org.jetbrains.annotations.NotNull;

class LTStyleProblemFilter extends ProblemFilter {
  @Override
  public boolean shouldIgnore(@NotNull TextProblem problem) {
    return problem instanceof LanguageToolChecker.Problem && problem.isStyleLike();
  }
}
