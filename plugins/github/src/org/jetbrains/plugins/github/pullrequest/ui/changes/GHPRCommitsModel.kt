// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.changes

import com.intellij.openapi.vcs.changes.Change
import org.jetbrains.plugins.github.api.data.GHCommit

interface GHPRCommitsModel {
  var commitsWithChanges: Map<GHCommit, List<Change>>?

  fun addStateChangesListener(listener: () -> Unit)
}
