// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.cherrypick

import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import git4idea.commands.GitLineEventDetector

class GitLocalChangesConflictDetector : GitLineEventDetector {
  override var isDetected = false
    private set

  var byMerge = false
    private set

  private val regex = "would be overwritten by ([a-z-]+)".toRegex(RegexOption.IGNORE_CASE)

  override fun onLineAvailable(line: @NlsSafe String?, outputType: Key<*>?) {
    if (line == null) return

    val matchResult = regex.find(line) ?: return
    isDetected = true
    byMerge = matchResult.groupValues[1] == "merge"
  }
}