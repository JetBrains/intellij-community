// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.ui

import com.intellij.util.EventDispatcher
import org.jetbrains.plugins.github.pullrequest.ui.SimpleEventListener
import javax.swing.Action
import kotlin.properties.Delegates

class GHSimpleErrorPanelModel(override val errorPrefix: String) : GHErrorPanelModel {

  private val changeEventDispatcher = EventDispatcher.create(SimpleEventListener::class.java)

  override var error by Delegates.observable<Throwable?>(null) { _, _, _ ->
    changeEventDispatcher.multicaster.eventOccurred()
  }
  override val errorAction: Action? = null


  override fun addAndInvokeChangeEventListener(listener: () -> Unit) =
    SimpleEventListener.addAndInvokeListener(changeEventDispatcher, listener)
}