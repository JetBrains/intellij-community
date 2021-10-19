// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.headertoolbar

import com.intellij.openapi.Disposable
import java.util.function.Consumer
import kotlin.properties.Delegates
import kotlin.reflect.KProperty

class ObservableValue<T>(initVal: T) {

  private val subscribers : MutableCollection<Consumer<T>> = mutableListOf()

  var value: T by Delegates.observable(initVal, this::handleUpdate)

  private fun handleUpdate(prop: KProperty<*>, oldValue: T, newValue: T) {
    subscribers.forEach { it.accept(newValue) }
  }

  fun subscribe(consumer: Consumer<T>): Disposable {
    subscribers.add(consumer)
    consumer.accept(value)
    return Disposable { subscribers.remove(consumer) }
  }
}