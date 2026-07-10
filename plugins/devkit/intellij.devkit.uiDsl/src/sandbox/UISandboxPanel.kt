// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox

import com.intellij.openapi.Disposable
import javax.swing.JComponent

internal interface UISandboxPanel {

  val title: String

  val isScrollbarNeeded: Boolean
    get() = true

  val isInternalApi: Boolean
    get() = false

  fun createContent(disposable: Disposable): JComponent
}