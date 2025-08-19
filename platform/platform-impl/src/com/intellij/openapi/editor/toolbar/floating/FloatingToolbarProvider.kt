// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.toolbar.floating

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.toolbar.floating.TransparentComponentAnimator.Companion.HIDING_TIME_MS
import com.intellij.openapi.editor.toolbar.floating.TransparentComponentAnimator.Companion.RETENTION_TIME_MS
import com.intellij.openapi.editor.toolbar.floating.TransparentComponentAnimator.Companion.SHOWING_TIME_MS
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.createExtensionDisposable
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus

@ApiStatus.OverrideOnly
@JvmDefaultWithCompatibility
interface FloatingToolbarProvider {
  @get:ApiStatus.ScheduledForRemoval
  @get:Deprecated("Use [order] option in plugin.xml")
  val priority: Int
    get() = 0

  val backgroundAlpha: Float
    get() = JBUI.CurrentTheme.FloatingToolbar.DEFAULT_BACKGROUND_ALPHA

  val showingTime: Int
    get() = SHOWING_TIME_MS

  val hidingTime: Int
    get() = HIDING_TIME_MS

  val retentionTime: Int
    get() = RETENTION_TIME_MS

  val autoHideable: Boolean
    get() = true

  val actionGroup: ActionGroup

  fun isApplicable(dataContext: DataContext): Boolean {
    val suppressors = EditorFloatingToolbarSuppressor.EP_NAME.extensionList
    return !suppressors.any { it.isSuppressed(this, dataContext) }
  }

  fun register(dataContext: DataContext, component: FloatingToolbarComponent, parentDisposable: Disposable) {}

  @ApiStatus.Internal
  fun onHiddenByEsc(dataContext: DataContext) {}

  companion object {

    val EP_NAME: ExtensionPointName<FloatingToolbarProvider> = ExtensionPointName.create("com.intellij.editorFloatingToolbarProvider")

    @Deprecated("Use the [ExtensionPointName.findExtensionOrFail] function directly")
    inline fun <reified T : FloatingToolbarProvider> getProvider(): T {
      return EP_NAME.findExtensionOrFail(T::class.java)
    }

    @Deprecated("Use the [ExtensionPointUtil.createExtensionDisposable] function directly")
    fun createExtensionDisposable(provider: FloatingToolbarProvider, parentDisposable: Disposable): Disposable {
      return EP_NAME.createExtensionDisposable(provider, parentDisposable)
    }
  }
}
