// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.ui.component

import com.intellij.util.EventDispatcher
import org.jetbrains.plugins.github.pullrequest.ui.GHLoadingErrorHandler
import org.jetbrains.plugins.github.pullrequest.ui.SimpleEventListener
import javax.swing.Action
import kotlin.properties.Delegates.observable

class GHHandledErrorPanelModel(override val errorPrefix: String,
                               private val errorHandler: GHLoadingErrorHandler) : GHErrorPanelModel {

  private val changeEventDispatcher = EventDispatcher.create(SimpleEventListener::class.java)

  override var error by observable<Throwable?>(null) { _, _, newValue ->
    errorAction = newValue?.let { errorHandler.getActionForError(it) }
    changeEventDispatcher.multicaster.eventOccurred()
  }
  override var errorAction: Action? = null
    private set


  override fun addAndInvokeChangeEventListener(listener: () -> Unit) =
    SimpleEventListener.addAndInvokeListener(changeEventDispatcher, listener)
}
