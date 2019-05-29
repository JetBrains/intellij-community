// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.actionSystem.DataContext

interface ChangesViewCommitWorkflowUi : CommitWorkflowUi {
  var isDefaultCommitActionEnabled: Boolean

  // TODO Looks better to create "interface ItemInclusionModel" to which ChangesTree will delegate
  //  And just pass such model to CommitWorkflowUi instead of adding include-related methods to CommitWorkflowUi directly
  fun isInclusionEmpty(): Boolean

  fun clearInclusion()

  fun showCommitOptions(options: CommitOptions, isFromToolbar: Boolean, dataContext: DataContext)
}