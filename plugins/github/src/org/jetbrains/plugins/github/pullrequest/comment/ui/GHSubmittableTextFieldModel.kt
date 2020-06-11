// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.util.EventDispatcher
import org.jetbrains.plugins.github.pullrequest.ui.SimpleEventListener
import org.jetbrains.plugins.github.util.handleOnEdt
import java.util.concurrent.CompletableFuture
import kotlin.properties.Delegates.observable

open class GHSubmittableTextFieldModel(initialText: String, private val submitter: (String) -> CompletableFuture<*>) {

  constructor(submitter: (String) -> CompletableFuture<*>) : this("", submitter)

  val document = EditorFactory.getInstance().createDocument(initialText)

  protected val stateEventDispatcher = EventDispatcher.create(SimpleEventListener::class.java)

  var isBusy by observable(false) { _, _, newValue ->
    document.setReadOnly(newValue)
    stateEventDispatcher.multicaster.eventOccurred()
  }
    protected set

  fun submit() {
    if (isBusy) return

    isBusy = true
    submitter(document.text).handleOnEdt { _, error ->
      if (error == null) runWriteAction {
        document.setText("")
      }
      isBusy = false
    }
  }

  fun addStateListener(listener: () -> Unit) = SimpleEventListener.addListener(stateEventDispatcher, listener)
}