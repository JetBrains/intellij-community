package org.jetbrains.android.uipreview;

import com.intellij.openapi.project.Project;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
public class ExceptionIssueMessage extends FixableIssueMessage {
  public ExceptionIssueMessage(@NotNull final Project project, @NotNull String message, @NotNull final Throwable throwable) {
    super(message + ' ', "Details", "", new Runnable() {
      @Override
      public void run() {
        AndroidUtils.showStackStace(project, new Throwable[]{throwable});
      }
    });
  }
}
