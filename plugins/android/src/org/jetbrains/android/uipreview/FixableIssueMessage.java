package org.jetbrains.android.uipreview;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

/**
* @author Eugene.Kudelevsky
*/
class FixableIssueMessage {
  final String myBeforeLinkText;
  final String myLinkText;
  final String myAfterLinkText;
  final Runnable myQuickFix;
  final Collection<Pair<String, Runnable>> myAdditionalFixes;

  FixableIssueMessage(@NotNull String beforeLinkText,
                      @NotNull String linkText,
                      @NotNull String afterLinkText,
                      @Nullable Runnable quickFix) {
    myBeforeLinkText = beforeLinkText;
    myLinkText = linkText;
    myAfterLinkText = afterLinkText;
    myQuickFix = quickFix;
    myAdditionalFixes = Collections.emptyList();
  }

  FixableIssueMessage(@NotNull String message) {
    this(message, "", "", null);
  }

  FixableIssueMessage(@NotNull String message, @NotNull Collection<Pair<String, Runnable>> quickFixes) {
    myBeforeLinkText = message;
    myLinkText = "";
    myAfterLinkText = "";
    myQuickFix = null;
    myAdditionalFixes = quickFixes;
  }
}
