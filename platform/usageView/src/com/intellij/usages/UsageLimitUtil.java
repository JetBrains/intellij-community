// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

public final class UsageLimitUtil {
  public static final int USAGES_LIMIT = getResultCountLimit();

  private static int getResultCountLimit() {
    return Registry.intValue("ide.find.result.count.warning.limit", 1000);
  }

  public enum Result {
    CONTINUE, ABORT
  }

  public static @NotNull Result showTooManyUsagesWarning(final @NotNull Project project, final @NotNull @NlsContexts.DialogMessage String message) {
    boolean result = runOrInvokeAndWait(() -> {
      String title = UsageViewBundle.message("find.excessive.usages.title");
      return MessageDialogBuilder.okCancel(title, message).yesText(UsageViewBundle.message("button.text.continue"))
        .noText(UsageViewBundle.message("button.text.abort")).icon(UIUtil.getWarningIcon()).ask(project);
    });
    return result ? Result.CONTINUE : Result.ABORT;
  }

  private static boolean runOrInvokeAndWait(final @NotNull Computable<Boolean> f) {
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