package org.jetbrains.android.uipreview;

import com.android.ide.common.rendering.api.RenderSession;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class RenderingResult {
  private final List<FixableIssueMessage> myWarnMessages;
  private final RenderSession mySession;

  public RenderingResult(@NotNull List<FixableIssueMessage> warnMessages, RenderSession session) {
    myWarnMessages = warnMessages;
    mySession = session;
  }

  @NotNull
  public List<FixableIssueMessage> getWarnMessages() {
    return myWarnMessages;
  }

  public RenderSession getSession() {
    return mySession;
  }
}
