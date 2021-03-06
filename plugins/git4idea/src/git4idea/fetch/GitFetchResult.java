// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.fetch;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public interface GitFetchResult {
  void showNotification();

  boolean showNotificationIfFailed();

  boolean showNotificationIfFailed(@NotNull @Nls String title);

  void throwExceptionIfFailed();
}
