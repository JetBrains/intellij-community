// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.util.EventDispatcher
import org.jetbrains.plugins.github.pullrequest.ui.SimpleEventListener
import org.jetbrains.plugins.github.util.handleOnEdt
import java.util.concurrent.CompletableFuture
import kotlin.properties.Delegates.observable

class GHSubmittableTextFieldModel(private val submitter: (String) -> CompletableFuture<*>) {

  val document = EditorFactory.getInstance().createDocument("")

  var isSubmitting by observable(false) { _, _, _ ->
    stateEventDispatcher.multicaster.eventOccurred()
  }
    private set

  var isLoading by observable(false) { _, _, _ ->
    stateEventDispatcher.multicaster.eventOccurred()
  }

  private val stateEventDispatcher = EventDispatcher.create(SimpleEventListener::class.java)

  fun submit() {
    if (isSubmitting) return

    isSubmitting = true
    submitter(document.text).handleOnEdt { _, error ->
      if (error == null) runWriteAction {
        document.setText("")
      }
      isSubmitting = false
    }
  }

  fun addStateListener(listener: () -> Unit) = SimpleEventListener.addListener(stateEventDispatcher, listener)
}