// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.instant

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal

@Experimental
@Internal
/**
 * If authentication is done via bearer token, [git4idea.instant.InstantGitTokenProvider.getToken] could be implemented,
 * otherwise, [git4idea.instant.InstantGitTokenProvider.getAuthHeaders] should provide authentication headers.
 */
interface InstantGitTokenProvider {

  companion object {
    val EP_NAME = ExtensionPointName.create<InstantGitTokenProvider>("Git4Idea.instantGitTokenProvider")
  }

  fun getToken(): String? {
    return null
  }

  fun getAuthHeaders(): Map<String, String>? {
    return getToken()?.let { mapOf("Authorization" to "Bearer $it") }
  }
}