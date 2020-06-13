// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.ui.GuiUtils;
import com.intellij.usageView.UsageViewBundle;
import org.jetbrains.annotations.NotNull;

public final class UsageLimitUtil {
  public static final int USAGES_LIMIT = 1000;

  public enum Result {
    CONTINUE, ABORT
  }

  @NotNull
  public static Result showTooManyUsagesWarning(@NotNull final Project project,
                                                @NotNull final String message,
                                                @NotNull final UsageViewPresentation usageViewPresentation) {
    int result = runOrInvokeAndWait(() -> {
      String title = UsageViewBundle.message("find.excessive.usages.title", usageViewPresentation.getUsagesWord(2));
      return Messages.showOkCancelDialog(project, message,
                                         title, UsageViewBundle.message("button.text.continue"), UsageViewBundle.message("button.text.abort"),
                                         Messages.getWarningIcon());
    });
    return result == Messages.OK ? Result.CONTINUE : Result.ABORT;
  }

  private static int runOrInvokeAndWait(@NotNull final Computable<Integer> f) {
    final int[] answer = new int[1];
    try {
      GuiUtils.runOrInvokeAndWait(() -> answer[0] = f.compute());
    }
    catch (Exception e) {
      answer[0] = 0;
    }

    return answer[0];
  }
}
