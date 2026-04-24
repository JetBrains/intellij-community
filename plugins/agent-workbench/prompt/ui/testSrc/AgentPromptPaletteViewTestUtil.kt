// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.ui.EditorTextField
import java.awt.Component
import java.awt.Container
import javax.swing.JPanel

internal fun layoutPopupRoot(root: JPanel) {
  root.size = root.preferredSize
  layoutRecursively(root)
}

internal fun layoutRecursively(component: Component) {
  if (component !is Container) {
    return
  }

  component.doLayout()
  component.components.forEach(::layoutRecursively)
}

internal fun findPromptArea(root: Component, promptArea: EditorTextField): EditorTextField? {
  return collectComponentsOfType(root, EditorTextField::class.java)
    .firstOrNull { it === promptArea }
}

internal fun <T : Component> collectComponentsOfType(root: Component, targetType: Class<T>): List<T> {
  val result = ArrayList<T>()
  collectComponents(root = root, targetType = targetType, sink = result)
  return result
}

private fun <T : Component> collectComponents(root: Component, targetType: Class<T>, sink: MutableList<T>) {
  if (targetType.isInstance(root)) {
    @Suppress("UNCHECKED_CAST")
    sink += root as T
  }

  if (root !is Container) {
    return
  }

  root.components.forEach { child ->
    collectComponents(root = child, targetType = targetType, sink = sink)
  }
}
