// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.fetch

import com.intellij.openapi.util.NlsContexts

interface GitFetchResult {
  fun showNotification()

  /**
   * @return true if fetch was successful, false otherwise
   */
  fun showNotificationIfFailed(): Boolean

  /**
   * @return true if fetch was successful, false otherwise
   */
  fun showNotificationIfFailed(title: @NlsContexts.NotificationTitle String): Boolean

  fun throwExceptionIfFailed()
}