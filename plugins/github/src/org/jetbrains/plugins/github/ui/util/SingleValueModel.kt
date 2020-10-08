// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.ui.util

import com.intellij.openapi.Disposable
import com.intellij.util.EventDispatcher
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.plugins.github.pullrequest.ui.SimpleEventListener
import org.jetbrains.plugins.github.util.GithubUtil

class SingleValueModel<T>(initialValue: T) : com.intellij.util.ui.codereview.SingleValueModel<T> {
  private val changeEventDispatcher = EventDispatcher.create(SimpleEventListener::class.java)

  override var value by GithubUtil.Delegates.observableField(initialValue, changeEventDispatcher)

  @RequiresEdt
  fun addAndInvokeValueChangedListener(listener: () -> Unit) =
    SimpleEventListener.addAndInvokeListener(changeEventDispatcher, listener)

  @RequiresEdt
  fun addValueChangedListener(disposable: Disposable, listener: () -> Unit) =
    SimpleEventListener.addDisposableListener(changeEventDispatcher, disposable, listener)

  @RequiresEdt
  fun addValueChangedListener(listener: () -> Unit) =
    SimpleEventListener.addListener(changeEventDispatcher, listener)

  @RequiresEdt
  override fun addValueUpdatedListener(listener: (newValue: T) -> Unit) {
    SimpleEventListener.addListener(changeEventDispatcher) {
      listener(value)
    }
  }

  fun <R> map(mapper: (T) -> R): SingleValueModel<R> {
    val mappedModel = SingleValueModel(value.let(mapper))
    addValueChangedListener {
      mappedModel.value = value.let(mapper)
    }
    return mappedModel
  }
}