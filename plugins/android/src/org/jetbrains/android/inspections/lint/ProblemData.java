package org.jetbrains.android.inspections.lint;

import com.android.tools.lint.detector.api.Issue;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class ProblemData {
  private final Issue myIssue;
  private final String myMessage;
  private final TextRange myTextRange;

  ProblemData(@NotNull Issue issue, @NotNull String message, @NotNull TextRange textRange) {
    myIssue = issue;
    myTextRange = textRange;
    myMessage = message;
  }

  @NotNull
  public Issue getIssue() {
    return myIssue;
  }

  @NotNull
  public TextRange getTextRange() {
    return myTextRange;
  }

  @NotNull
  public String getMessage() {
    return myMessage;
  }
}
