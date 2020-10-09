// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.simpleTitleParts

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener

open class TitleInfoOption {
  companion object {
    @JvmStatic
    val ALWAYS_ACTIVE = TitleInfoOption()
  }

  var isActive: Boolean = true
    set(value) {
      if (field == value) {
        return
      }
      field = value
      update()
    }

  var listener: (() -> Unit)? = null

  protected open fun update() {
    listener?.let { it() }
  }
}

class RegistryOption(var key: String, disposable: Disposable?) : TitleInfoOption() {
  init {
    Registry.get(key).addListener(object : RegistryValueListener {
      override fun afterValueChanged(value: RegistryValue) {
        checkState()
      }
    }, disposable ?: ApplicationManager.getApplication())

    checkState()
  }

  private fun checkState() {
    isActive = Registry.get(key).asBoolean()
  }
}

class VMOOption(key: String) : TitleInfoOption() {
  init {
    isActive = java.lang.Boolean.getBoolean(key)
  }
}

