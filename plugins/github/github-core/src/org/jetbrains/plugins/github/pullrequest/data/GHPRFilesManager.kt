// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.util.concurrency.annotations.RequiresEdt

internal interface GHPRFilesManager {
  val id: String

  @RequiresEdt
  fun createAndOpenTimelineFile(prId: GHPRIdentifier, requestFocus: Boolean)

  @RequiresEdt
  fun createAndOpenDiffFile(prId: GHPRIdentifier?, requestFocus: Boolean)

  @RequiresEdt
  fun updateTimelineFilePresentation(prId: GHPRIdentifier)

  suspend fun closeNewPrFile()

  suspend fun closeAllFiles()
}
