// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.vcs.changes.InclusionModel
import com.intellij.openapi.vcs.changes.LocalChangeList

interface ChangesViewCommitWorkflowUi : CommitWorkflowUi {
  var isDefaultCommitActionEnabled: Boolean
  fun setCustomCommitActions(actions: List<AnAction>)

  var inclusionModel: InclusionModel?
  // TODO Looks better to create "interface ItemInclusionModel" to which ChangesTree will delegate
  //  And just pass such model to CommitWorkflowUi instead of adding include-related methods to CommitWorkflowUi directly
  fun isInclusionEmpty(): Boolean

  fun getInclusion(): Set<Any>

  fun clearInclusion()
  fun retainInclusion(items: Collection<*>)

  fun showCommitOptions(options: CommitOptions, isFromToolbar: Boolean, dataContext: DataContext)

  fun setCompletionContext(changeLists: List<LocalChangeList>)
}