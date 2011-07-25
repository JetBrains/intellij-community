package org.jetbrains.android.uipreview;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
* @author Eugene.Kudelevsky
*/
class RenderingErrorMessage {
  final String myBeforeLinkText;
  final String myLinkText;
  final String myAfterLinkText;
  final Runnable myQuickFix;

  RenderingErrorMessage(@NotNull String beforeLinkText,
                        @NotNull String linkText,
                        @NotNull String afterLinkText,
                        @Nullable Runnable quickFix) {
    myBeforeLinkText = beforeLinkText;
    myLinkText = linkText;
    myAfterLinkText = afterLinkText;
    myQuickFix = quickFix;
  }

  RenderingErrorMessage(@NotNull String message) {
    this(message, "", "", null);
  }
}
