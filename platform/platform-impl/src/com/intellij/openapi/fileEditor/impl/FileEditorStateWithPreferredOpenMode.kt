// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.openapi.fileEditor.FileEditorState
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
interface FileEditorStateWithPreferredOpenMode : FileEditorState {
  val openMode: FileEditorManagerImpl.OpenMode?
}