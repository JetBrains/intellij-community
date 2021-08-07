package com.intellij.grazie.ide.language.xml;

import com.intellij.grazie.text.ProblemFilter;
import com.intellij.grazie.text.TextProblem;
import org.jetbrains.annotations.NotNull;

class XmlProblemFilter extends ProblemFilter {
  @Override
  public boolean shouldIgnore(@NotNull TextProblem problem) {
    // This HTML (<b>text</b>) is currently split into 3 parts, and the bracket-pairing rule produces false positives.
    // This can be removed when we implement smarter tag content merging.
    String id = problem.getRule().getGlobalId();
    return id.startsWith("LanguageTool.") && id.endsWith("UNPAIRED_BRACKETS");
  }
}
