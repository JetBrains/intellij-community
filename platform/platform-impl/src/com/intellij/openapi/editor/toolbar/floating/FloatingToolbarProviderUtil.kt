// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("FloatingToolbarProviderUtil")
package com.intellij.openapi.editor.toolbar.floating

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import com.intellij.util.ui.UIUtil


fun FloatingToolbarProvider.scheduleShowAllToolbarComponents(project: Project) {
  forEachToolbarComponent(project) { it.scheduleShow() }
}

fun FloatingToolbarProvider.scheduleHideAllToolbarComponents(project: Project) {
  forEachToolbarComponent(project) { it.scheduleHide() }
}

private fun FloatingToolbarProvider.forEachToolbarComponent(project: Project, consumer: (FloatingToolbarComponent) -> Unit) {
  UIUtil.uiTraverser(null)
    .withRoot(WindowManager.getInstance().getFrame(project))
    .filterIsInstance(FloatingToolbarComponent::class.java)
    .filter { it.providerId == id }
    .forEach(consumer)
}