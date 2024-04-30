// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
@Experimental
interface UndoRedoListener {
  companion object {
    val TOPIC: Topic<UndoRedoListener> = Topic(UndoRedoListener::class.java, Topic.BroadcastDirection.NONE)
  }

  fun undoRedoStarted(project: Project?, undoManager: UndoManager, editor: FileEditor?, isUndo: Boolean, disposable: Disposable)
}