// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.ui.util

import com.intellij.openapi.Disposable
import com.intellij.util.EventDispatcher
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.plugins.github.pullrequest.ui.SimpleEventListener
import org.jetbrains.plugins.github.util.GithubUtil

class SingleValueModel<T>(initialValue: T) {
  private val changeEventDispatcher = EventDispatcher.create(SimpleEventListener::class.java)

  var value by GithubUtil.Delegates.observableField(initialValue, changeEventDispatcher)

  @CalledInAwt
  fun addAndInvokeValueChangedListener(listener: () -> Unit) =
    SimpleEventListener.addAndInvokeListener(changeEventDispatcher, listener)

  @CalledInAwt
  fun addValueChangedListener(disposable: Disposable, listener: () -> Unit) =
    SimpleEventListener.addDisposableListener(changeEventDispatcher, disposable, listener)

  @CalledInAwt
  fun addValueChangedListener(listener: () -> Unit) =
    SimpleEventListener.addListener(changeEventDispatcher, listener)

  fun <R> map(mapper: (T) -> R): SingleValueModel<R> {
    val mappedModel = SingleValueModel(value.let(mapper))
    addValueChangedListener {
      mappedModel.value = value.let(mapper)
    }
    return mappedModel
  }
}