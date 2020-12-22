// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.toolbar.floating

import com.intellij.util.ui.UIUtil
import java.awt.Window


fun FloatingToolbarProvider.scheduleShowAllToolbarComponents() {
  forEachToolbarComponent { it.scheduleShow() }
}

fun FloatingToolbarProvider.scheduleHideAllToolbarComponents() {
  forEachToolbarComponent { it.scheduleHide() }
}

private fun FloatingToolbarProvider.forEachToolbarComponent(consumer: (FloatingToolbarComponent) -> Unit) {
  UIUtil.uiTraverser(null)
    .withRoots(*Window.getWindows())
    .filterIsInstance(FloatingToolbarComponent::class.java)
    .filter { it.provider === this }
    .forEach(consumer)
}