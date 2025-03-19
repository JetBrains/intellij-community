// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commit

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.vcs.commit.CommitMode
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface GitCommitModeProvider {
  fun getCommitMode(): CommitMode?

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<GitCommitModeProvider> = ExtensionPointName("Git4Idea.gitCommitModeProvider")
  }
}
