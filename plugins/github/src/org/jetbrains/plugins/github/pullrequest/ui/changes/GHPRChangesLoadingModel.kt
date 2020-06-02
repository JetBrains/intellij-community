// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.changes

import com.intellij.openapi.Disposable
import org.jetbrains.plugins.github.pullrequest.data.GHPRChangesProvider
import org.jetbrains.plugins.github.pullrequest.ui.GHCompletableFutureLoadingModel
import org.jetbrains.plugins.github.pullrequest.ui.GHLoadingModel

class GHPRChangesLoadingModel(val commitsModel: GHPRCommitsModel,
                              val cumulativeChangesModel: GHPRChangesModel,
                              disposable: Disposable)
  : GHCompletableFutureLoadingModel<GHPRChangesProvider>(disposable) {

  init {
    addStateChangeListener(object : GHLoadingModel.StateChangeListener {
      override fun onLoadingCompleted() {
        if (resultAvailable) {
          commitsModel.commitsWithChanges = result!!.changesByCommits
          cumulativeChangesModel.changes = result!!.changes
        }
        else {
          commitsModel.commitsWithChanges = null
          cumulativeChangesModel.changes = null
        }
      }
    })
  }
}