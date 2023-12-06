// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.instant

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal

@Experimental
@Internal
interface InstantGitTokenProvider {

  companion object {
    val EP_NAME = ExtensionPointName.create<InstantGitTokenProvider>("Git4Idea.instantGitTokenProvider")
  }

  /**
   * Use it as "Authorization" to "Bearer $token" header.
   */
  fun getToken(): String?
}