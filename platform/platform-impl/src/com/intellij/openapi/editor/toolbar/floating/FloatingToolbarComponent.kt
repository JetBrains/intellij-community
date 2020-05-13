// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.toolbar.floating

import com.intellij.openapi.actionSystem.DataKey

interface FloatingToolbarComponent {

  fun update()

  fun scheduleHide()

  fun scheduleShow()

  companion object {
    val KEY = DataKey.create<FloatingToolbarComponent>("com.intellij.openapi.editor.toolbar.floating.Component")
  }
}