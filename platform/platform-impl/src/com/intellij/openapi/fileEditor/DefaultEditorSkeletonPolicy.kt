// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor

import com.intellij.openapi.fileEditor.impl.EditorSkeletonPolicy
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.ThreeState

internal class DefaultEditorSkeletonPolicy : EditorSkeletonPolicy {
  override fun shouldShowSkeleton(fileEditorComposite: FileEditorComposite): ThreeState {
    val isEnabled = Registry.`is`("editor.skeleton.enabled", false)
    return ThreeState.fromBoolean(isEnabled)
  }

  override fun getSkeletonDelayMs(fileEditorComposite: FileEditorComposite): Long {
    return Registry.intValue("editor.skeleton.delay.ms", 50).toLong()
  }
}
