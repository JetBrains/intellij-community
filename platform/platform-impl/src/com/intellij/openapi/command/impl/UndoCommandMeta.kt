// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl

import com.intellij.openapi.command.undo.CommandMeta
import org.jetbrains.annotations.ApiStatus


@ApiStatus.Experimental
@ApiStatus.Internal
interface UndoCommandMeta : CommandMeta {
  fun editorProviders(): List<ForeignEditorProvider>
}
