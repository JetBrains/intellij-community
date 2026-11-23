// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.skeleton.layout.components

import java.awt.Graphics2D

internal class EditorSkeletonSpace(
  override val preferredWidth: Int,
  override val preferredHeight: Int,
) : EditorSkeletonComponent() {
  override fun paintComponent(g: Graphics2D, width: Int, height: Int) {
  }
}
