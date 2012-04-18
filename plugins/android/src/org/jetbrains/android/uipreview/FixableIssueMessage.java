package org.jetbrains.android.uipreview;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
* @author Eugene.Kudelevsky
*/
public class FixableIssueMessage {
  public final String myBeforeLinkText;
  public final String myLinkText;
  public final String myAfterLinkText;
  public final Runnable myQuickFix;
  public final List<Pair<String, Runnable>> myAdditionalFixes;

  public FixableIssueMessage(@NotNull String beforeLinkText,
                      @NotNull String linkText,
                      @NotNull String afterLinkText,
                      @Nullable Runnable quickFix) {
    myBeforeLinkText = beforeLinkText;
    myLinkText = linkText;
    myAfterLinkText = afterLinkText;
    myQuickFix = quickFix;
    myAdditionalFixes = Collections.emptyList();
  }

  public FixableIssueMessage(@NotNull String message) {
    this(message, "", "", null);
  }

  public FixableIssueMessage(@NotNull String message, @NotNull List<Pair<String, Runnable>> quickFixes) {
    myBeforeLinkText = message;
    myLinkText = "";
    myAfterLinkText = "";
    myQuickFix = null;
    myAdditionalFixes = quickFixes;
  }
}
