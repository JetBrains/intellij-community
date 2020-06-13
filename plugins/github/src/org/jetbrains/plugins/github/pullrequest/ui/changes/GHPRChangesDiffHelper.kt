// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.changes

import com.intellij.diff.chains.DiffRequestChain
import com.intellij.openapi.ListSelection
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.vcs.changes.Change

interface GHPRChangesDiffHelper {

  fun getRequestChain(changes: ListSelection<Change>): DiffRequestChain

  companion object {
    val DATA_KEY = DataKey.create<GHPRChangesDiffHelper>("Github.PullRequest.Diff.Helper")
  }
}
