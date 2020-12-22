// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.toolbar.floating

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.toolbar.floating.FloatingToolbarProviderBean.Companion.resolveActionGroup
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.reflect.jvm.jvmName

@ApiStatus.ScheduledForRemoval(inVersion = "2022.1")
@Deprecated("Use FloatingToolbarProviderBean instead")
abstract class AbstractFloatingToolbarProvider(actionGroupId: String) : FloatingToolbarProvider {

  override val id by lazy { this::class.jvmName }

  override val actionGroup = resolveActionGroup(actionGroupId)

  private val toolbars = CopyOnWriteArrayList<FloatingToolbarComponent>()

  override fun register(toolbar: FloatingToolbarComponent, parentDisposable: Disposable) {
    toolbars.add(toolbar)
    Disposer.register(parentDisposable, Disposable { toolbars.remove(toolbar) })
  }

  fun scheduleShowAllToolbarComponents() {
    toolbars.forEach { it.scheduleShow() }
  }

  fun scheduleHideAllToolbarComponents() {
    toolbars.forEach { it.scheduleHide() }
  }
}