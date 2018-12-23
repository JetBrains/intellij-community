// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.changes.Change

class AlienCommitWorkflow(val vcs: AbstractVcs<*>, changeListName: String, changes: List<Change>, commitMessage: String?) :
  DialogCommitWorkflow(vcs.project, changes, vcsToCommit = vcs, initialCommitMessage = commitMessage) {
  val changeList = AlienLocalChangeList(changes, changeListName)
}