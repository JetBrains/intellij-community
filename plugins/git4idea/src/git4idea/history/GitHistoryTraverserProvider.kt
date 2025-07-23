// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.history

import com.intellij.openapi.project.Project
import com.intellij.vcs.log.impl.VcsProjectLog

fun getTraverser(project: Project): GitHistoryTraverser? {
  val logData = VcsProjectLog.getInstance(project).dataManager
                  ?.takeIf { it.dataPack.isFull } ?: return null
  return GitHistoryTraverserImpl(project, logData)
}