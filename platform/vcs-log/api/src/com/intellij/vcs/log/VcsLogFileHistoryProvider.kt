// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface VcsLogFileHistoryProvider {
  fun canShowFileHistory(paths: Collection<FilePath>, revisionNumber: VcsRevisionNumber?): Boolean

  fun showFileHistory(paths: Collection<FilePath>, revisionNumber: VcsRevisionNumber?)

  fun showFileHistory(paths: Collection<FilePath>, revisionNumber: VcsRevisionNumber?, revisionToSelect: VcsRevisionNumber)
}
