// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor

import com.intellij.openapi.fileEditor.impl.EditorSkeletonPolicy
import com.intellij.openapi.util.registry.Registry

internal class DefaultEditorSkeletonPolicy : EditorSkeletonPolicy {
  override fun shouldShowSkeleton(fileEditorComposite: FileEditorComposite): Boolean {
    return Registry.`is`("editor.skeleton.enabled", false)
  }
}
