package org.jetbrains.android.uipreview;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class RenderingResult {
  private final List<FixableIssueMessage> myWarnMessages;

  public RenderingResult(@NotNull List<FixableIssueMessage> warnMessages) {
    myWarnMessages = warnMessages;
  }

  @NotNull
  public List<FixableIssueMessage> getWarnMessages() {
    return myWarnMessages;
  }
}
