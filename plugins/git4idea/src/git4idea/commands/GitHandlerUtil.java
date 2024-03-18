// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.commands;

import com.intellij.openapi.progress.ProgressIndicator;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @deprecated use {@link GitImpl}
 */
@Deprecated
public final class GitHandlerUtil {

  private GitHandlerUtil() {
  }

  @Deprecated(forRemoval = true)
  public static void runInCurrentThread(@NotNull GitHandler handler,
                                        @Nullable ProgressIndicator indicator,
                                        boolean setIndeterminateFlag,
                                        @Nullable @Nls String operationName) {
    handler.runInCurrentThread(() -> {
      if (indicator != null) {
        indicator.setText(operationName == null ? GitBundle.message("git.running", handler.printableCommandLine()) : operationName);
        indicator.setText2("");
        if (setIndeterminateFlag) {
          indicator.setIndeterminate(true);
        }
      }
    });
  }

  public static boolean isErrorLine(@NotNull String text) {
    for (String prefix : GitImplBase.ERROR_INDICATORS) {
      if (text.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }
}
