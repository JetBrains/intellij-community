// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl

import com.intellij.openapi.command.undo.DocumentReference
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal


@Experimental
@Internal
interface UndoMeta {
  fun undoProject(): Project?
  fun focusedEditor(): FileEditor?
  fun focusedDocument(): DocumentReference?
}
