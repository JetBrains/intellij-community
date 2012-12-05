package org.jetbrains.android.uipreview;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
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
  public final List<String> myTips = new ArrayList<String>();

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

  public void addTip(@NotNull String tipText) {
    myTips.add(tipText);
  }

  public static FixableIssueMessage createExceptionIssue(@NotNull final Project project,
                                                         @NotNull String message,
                                                         @NotNull final Throwable throwable) {
    return new FixableIssueMessage(message + ' ', "Details", "", new Runnable() {
      @Override
      public void run() {
        AndroidUtils.showStackStace(project, new Throwable[]{throwable});
      }
    });
  }
}
