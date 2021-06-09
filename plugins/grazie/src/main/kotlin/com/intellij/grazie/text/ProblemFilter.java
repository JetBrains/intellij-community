package com.intellij.grazie.text;

import org.jetbrains.annotations.NotNull;

/**
 * An extension allowing to prevent some text problems from being reported,
 * registered in {@code plugin.xml} under {@code "com.intellij.grazie.problemFilter"} qualified name.
 */
public abstract class ProblemFilter {
  /** @return whether the given problem should not be reported by grammar checking highlighting */
  public abstract boolean shouldIgnore(@NotNull TextProblem problem);
}
