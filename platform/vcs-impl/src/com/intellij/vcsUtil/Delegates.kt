// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcsUtil

import kotlin.properties.ObservableProperty
import kotlin.reflect.KProperty

object Delegates {
    inline fun <T> equalVetoingObservable(initialValue: T, crossinline onChange: (newValue: T) -> Unit) =
      object : ObservableProperty<T>(initialValue) {
        override fun beforeChange(property: KProperty<*>, oldValue: T, newValue: T) = newValue == null || oldValue != newValue
        override fun afterChange(property: KProperty<*>, oldValue: T, newValue: T) = onChange(newValue)
      }
  }
