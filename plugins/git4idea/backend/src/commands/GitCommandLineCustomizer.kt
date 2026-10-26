// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commands

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

/**
 * Changes the command line of a Git command before the command starts.
 */
@ApiStatus.Internal
interface GitCommandLineCustomizer {
  fun customize(handler: GitHandler, commandLine: GeneralCommandLine)

  companion object {
    private val EP_NAME: ExtensionPointName<GitCommandLineCustomizer> =
      ExtensionPointName.create("Git4Idea.gitCommandLineCustomizer")

    @JvmStatic
    fun apply(handler: GitHandler, commandLine: GeneralCommandLine) {
      EP_NAME.forEachExtensionSafe { customizer ->
        customizer.customize(handler, commandLine)
      }
    }
  }
}
