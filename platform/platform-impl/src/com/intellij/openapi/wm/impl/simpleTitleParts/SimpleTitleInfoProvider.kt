// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.simpleTitleParts

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.constraints.isDisposed
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.openapi.wm.impl.IdeFrameDecorator
import com.intellij.openapi.wm.impl.TitleInfoProvider

abstract class SimpleTitleInfoProvider(val project: Project) : TitleInfoProvider {
  protected var enabled = false

  private fun getRegisterKey(): String? {
    return if(IdeFrameDecorator.isCustomDecorationActive()) borderlessRegistryKey else defaultRegistryKey
  }

  private var updateListener: HashSet<(() -> Unit)> = HashSet()
  private var registryDisposable: Disposable? = null

  private val registryListener = object : RegistryValueListener {
    override fun afterValueChanged(value: RegistryValue) {
      updateSubscriptions()
    }
  }

  override fun addUpdateListener(value: () -> Unit, disposable: Disposable?) {
    updateListener.add(value)
    updateSubscriptions()

    disposable?.let {
      Disposer.register(it, Disposable{ updateSubscriptions() })
    }
  }

  protected open fun updateSubscriptions() {
    enabled = isEnabled() && updateListener.isNotEmpty()

    getRegisterKey()?.let {key ->
      if(updateListener.isNotEmpty()) {
        if (registryDisposable == null || registryDisposable?.isDisposed == true) {
          val rds = Disposer.newDisposable()
          Disposer.register(project, rds)
          Registry.get(key).addListener(registryListener, rds)

          registryDisposable = rds
        }
        else {
          registryDisposable?.let {
            if (!it.isDisposed) it.dispose()
          }
        }
      }
    }

    updateValue()
  }

  protected open fun updateValue() {
    borderlessTitlePart.active = isActive
    borderlessTitlePart.longText = value

    updateNotify()
  }

  protected open fun isEnabled(): Boolean {
    return getRegisterKey()?.let {
      Registry.get(it).asBoolean()} ?: true
  }

  private fun updateNotify() {
    updateListener.forEach { it() }
  }

  override val isActive: Boolean
    get() = enabled

  protected abstract val defaultRegistryKey: String?
  protected abstract val borderlessRegistryKey: String?
}