// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

public final class UsageLimitUtil {
  public static final int USAGES_LIMIT = 1000;

  public enum Result {
    CONTINUE, ABORT
  }

  @NotNull
  public static Result showTooManyUsagesWarning(@NotNull final Project project, @NotNull final @NlsContexts.DialogMessage String message) {
    boolean result = runOrInvokeAndWait(() -> {
      String title = UsageViewBundle.message("find.excessive.usages.title");
      return MessageDialogBuilder.okCancel(title, message).yesText(UsageViewBundle.message("button.text.continue"))
        .noText(UsageViewBundle.message("button.text.abort")).icon(UIUtil.getWarningIcon()).ask(project);
    });
    return result ? Result.CONTINUE : Result.ABORT;
  }

  private static boolean runOrInvokeAndWait(@NotNull final Computable<Boolean> f) {
    final boolean[] answer = new boolean[1];
    try {
      ApplicationManager.getApplication().invokeAndWait(() -> answer[0] = f.compute());
    }
    catch (Exception e) {
      answer[0] = true;
    }

    return answer[0];
  }
}