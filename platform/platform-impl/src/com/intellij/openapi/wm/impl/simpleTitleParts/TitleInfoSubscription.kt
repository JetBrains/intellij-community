// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.simpleTitleParts

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener

open class TitleInfoSubscription {
  companion object {
    @JvmStatic
    val ALWAYS_ACTIVE = TitleInfoSubscription()
  }

  var isActive: Boolean = true
  var listener: (() -> Unit)? = null

  protected open fun update() {
    listener?.let { it() }
  }
}

class RegistrySubscriptionList(val keyList: List<String>, disposable: Disposable) : TitleInfoSubscription() {
  val subscript: List<TitleInfoSubscription>
  init {
    subscript = keyList.map {
      val subscription = RegistrySubscription(it, disposable)
      subscription.listener = { update() }
      subscription
    }.toList()
  }

  override fun update() {
    isActive = subscript.isEmpty() || subscript.any { it.isActive }
    super.update()
  }
}

class RegistrySubscription(key: String, disposable: Disposable) : TitleInfoSubscription() {
  init {
    val rds = Disposer.newDisposable()
    Disposer.register(disposable, rds)
    Registry.get(key).addListener(object : RegistryValueListener {
      override fun afterValueChanged(value: RegistryValue) {
        isActive = Registry.get(key).asBoolean()
        update()
      }
    }, rds)
  }
}

class VMOSubscription(key: String) : TitleInfoSubscription() {
  init {
    isActive = java.lang.Boolean.getBoolean(key)
  }
}

