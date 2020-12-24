// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.toolbar.floating

import com.intellij.openapi.Disposable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.ScheduledForRemoval(inVersion = "2022.1")
@Deprecated("Use FloatingToolbarProviderBean instead")
interface EditorFloatingToolbarProvider : FloatingToolbarProvider {

  val priority: Int

  fun register(toolbar: FloatingToolbarComponent, parentDisposable: Disposable)
}