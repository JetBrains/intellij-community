// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.vcs.changes.InclusionModel
import com.intellij.openapi.vcs.changes.LocalChangeList

interface ChangesViewCommitWorkflowUi : NonModalCommitWorkflowUi {
  val isActive: Boolean
  fun deactivate(isRestoreState: Boolean)

  fun endExecution()

  var inclusionModel: InclusionModel?

  fun expand(item: Any)
  fun select(item: Any)
  fun selectFirst(items: Collection<Any>)

  fun setCompletionContext(changeLists: List<LocalChangeList>)
}