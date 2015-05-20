/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.usages;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.GuiUtils;
import com.intellij.usageView.UsageViewBundle;
import org.jetbrains.annotations.NotNull;

/**
 * User: cdr
 */
public class UsageLimitUtil {
  public static final int USAGES_LIMIT = 1000;

  public static void showAndCancelIfAborted(@NotNull Project project,
                                            @NotNull String message,
                                            @NotNull UsageViewPresentation usageViewPresentation) {
    Result retCode = showTooManyUsagesWarning(project, message, usageViewPresentation);

    if (retCode != Result.CONTINUE) {
      throw new ProcessCanceledException();
    }
  }

  public enum Result {
    CONTINUE, ABORT
  }

  @NotNull
  public static Result showTooManyUsagesWarning(@NotNull final Project project,
                                                @NotNull final String message,
                                                @NotNull final UsageViewPresentation usageViewPresentation) {
    int result = runOrInvokeAndWait(new Computable<Integer>() {
      @Override
      public Integer compute() {
        String title = UsageViewBundle.message("find.excessive.usages.title", StringUtil.capitalize(StringUtil.pluralize(usageViewPresentation.getUsagesWord())));
        return Messages.showOkCancelDialog(project, message,
                                           title, UsageViewBundle.message("button.text.continue"), UsageViewBundle.message("button.text.abort"),
                                           Messages.getWarningIcon());
      }
    });
    return result == Messages.OK ? Result.CONTINUE : Result.ABORT;
  }

  private static int runOrInvokeAndWait(@NotNull final Computable<Integer> f) {
    final int[] answer = new int[1];
    try {
      GuiUtils.runOrInvokeAndWait(new Runnable() {
        @Override
        public void run() {
          answer[0] = f.compute();
        }
      });
    }
    catch (Exception e) {
      answer[0] = 0;
    }

    return answer[0];
  }
}
