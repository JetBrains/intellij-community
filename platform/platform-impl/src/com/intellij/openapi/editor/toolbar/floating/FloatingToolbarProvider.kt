// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.toolbar.floating

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.ExtensionPointUtil
import com.intellij.openapi.observable.util.whenDisposed
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.ApiStatus

interface FloatingToolbarProvider {
  @get:ApiStatus.ScheduledForRemoval
  @get:Deprecated("Use [order] option in plugin.xml")
  val priority: Int
    get() = 0

  val autoHideable: Boolean

  val actionGroup: ActionGroup

  fun isApplicable(dataContext: DataContext): Boolean = true

  fun register(dataContext: DataContext, component: FloatingToolbarComponent, parentDisposable: Disposable) {}

  companion object {
    val EP_NAME = ExtensionPointName.create<FloatingToolbarProvider>("com.intellij.editorFloatingToolbarProvider")

    inline fun <reified T : FloatingToolbarProvider> getProvider(): T {
      return EP_NAME.findExtensionOrFail(T::class.java)
    }

    fun createExtensionDisposable(provider: FloatingToolbarProvider, parentDisposable: Disposable): Disposable {
      return ExtensionPointUtil.createExtensionDisposable(provider, EP_NAME)
        .also { parentDisposable.whenDisposed { Disposer.dispose(it) } }
    }
  }
}