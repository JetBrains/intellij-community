// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.annotate

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import org.jetbrains.annotations.ApiStatus.Internal
import kotlin.time.Duration

@Internal
interface GitAnnotationPerformanceListener {

  companion object {
    @JvmField
    val EP_NAME = ExtensionPointName.create<GitAnnotationPerformanceListener>("Git4Idea.gitAnnotationPerformanceListener")
  }

  fun onAnnotationFinished(project: Project, path: FilePath, revision: VcsRevisionNumber?, duration: Duration, provider: String)
}