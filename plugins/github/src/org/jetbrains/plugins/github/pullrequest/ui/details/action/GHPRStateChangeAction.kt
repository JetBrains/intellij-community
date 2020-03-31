// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.details.action

import org.jetbrains.plugins.github.ui.util.SingleValueModel
import org.jetbrains.plugins.github.util.errorOnEdt
import org.jetbrains.plugins.github.util.handleOnEdt
import java.awt.event.ActionEvent
import java.util.concurrent.CompletableFuture
import javax.swing.AbstractAction

abstract class GHPRStateChangeAction(actionName: String,
                                     private val busyStateModel: SingleValueModel<Boolean>,
                                     private val errorHandler: (String) -> Unit) : AbstractAction(actionName) {

  abstract val errorPrefix: String

  init {
    busyStateModel.addValueChangedListener {
      update()
    }
  }

  protected fun update() {
    isEnabled = computeEnabled()
  }

  protected open fun computeEnabled(): Boolean = !busyStateModel.value

  override fun actionPerformed(e: ActionEvent) {
    if (busyStateModel.value) return
    busyStateModel.value = true
    errorHandler("")
    val task = submitTask()
    if (task != null) task.errorOnEdt { error ->
      //language=HTML
      errorHandler("<p>${errorPrefix}</p>" + "<p>${error.message.orEmpty()}</p>")
    }.handleOnEdt { _, _ ->
      busyStateModel.value = false
    }
    else {
      busyStateModel.value = false
    }
  }

  abstract fun submitTask(): CompletableFuture<Unit>?

}