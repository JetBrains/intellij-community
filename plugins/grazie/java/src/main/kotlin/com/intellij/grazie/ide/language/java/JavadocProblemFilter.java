package com.intellij.grazie.ide.language.java;

import com.intellij.grazie.text.ProblemFilter;
import com.intellij.grazie.text.TextContent;
import com.intellij.grazie.text.TextProblem;
import com.intellij.grazie.utils.ProblemFilterUtil;
import com.intellij.psi.javadoc.PsiDocTag;
import org.jetbrains.annotations.NotNull;

class JavadocProblemFilter extends ProblemFilter {
  @Override
  public boolean shouldIgnore(@NotNull TextProblem problem) {
    if (problem.getText().getDomain() == TextContent.TextDomain.DOCUMENTATION &&
        problem.getText().getCommonParent() instanceof PsiDocTag &&
        (ProblemFilterUtil.isUndecoratedSingleSentenceIssue(problem) || ProblemFilterUtil.isInitialCasingIssue(problem))) {
      return true;
    }
    return false;
  }
}
