// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.fetch;

import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;

public interface GitFetchResult {
  void showNotification();

  /**
   * @return true if fetch was successful, false otherwise
   */
  boolean showNotificationIfFailed();

  /**
   * @return true if fetch was successful, false otherwise
   */
  boolean showNotificationIfFailed(@NotNull @NlsContexts.NotificationTitle String title);

  void throwExceptionIfFailed();
}
