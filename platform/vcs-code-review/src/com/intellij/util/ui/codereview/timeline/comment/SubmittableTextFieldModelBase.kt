// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.codereview.timeline.comment

import com.intellij.openapi.editor.EditorFactory
import com.intellij.util.EventDispatcher
import com.intellij.util.ui.codereview.SimpleEventListener

abstract class SubmittableTextFieldModelBase(initialText: String) : SubmittableTextFieldModel {
  private val listeners = EventDispatcher.create(SimpleEventListener::class.java)

  override val document = EditorFactory.getInstance().createDocument(initialText)

  override var isBusy = false
    protected set(value) {
      field = value
      listeners.multicaster.eventOccurred()
    }

  override var error: Throwable? = null
    protected set(value) {
      field = value
      listeners.multicaster.eventOccurred()
    }

  override fun addStateListener(listener: SimpleEventListener) {
    listeners.addListener(listener)
  }
}