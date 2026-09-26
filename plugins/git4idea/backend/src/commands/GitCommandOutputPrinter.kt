// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commands

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
interface GitCommandOutputPrinter {
  fun showCommandStart(processId: String, workingDir: Path, commandLine: String)

  fun showCommandOutput(processId: String, workingDir: Path, outputType: Key<*>, line: String)

  fun showCommandFinished(processId: String, workingDir: Path, exitCode: Int)

  companion object {
    @JvmStatic
    fun getInstance(project: Project): GitCommandOutputPrinter = project.service()
  }
}