package com.intellij.grazie.ide.language.java;

import com.intellij.grazie.text.ProblemFilter;
import com.intellij.grazie.text.RuleGroup;
import com.intellij.grazie.text.TextContent;
import com.intellij.grazie.text.TextProblem;
import com.intellij.grazie.utils.Text;
import org.jetbrains.annotations.NotNull;

class JavadocProblemFilter extends ProblemFilter {
  @Override
  public boolean shouldIgnore(@NotNull TextProblem problem) {
    if (problem.getText().getDomain() == TextContent.TextDomain.DOCUMENTATION && Text.isSingleSentence(problem.getText())) {
      return problem.fitsGroup(RuleGroup.UNDECORATED_SINGLE_SENTENCE);
    }
    return false;
  }
}
