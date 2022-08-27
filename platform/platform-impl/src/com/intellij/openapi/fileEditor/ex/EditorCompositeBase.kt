// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.ex

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.util.UserDataHolderBase

abstract class EditorCompositeBase : UserDataHolderBase() {
  abstract val allEditors: List<FileEditor>
  abstract val allProviders: List<FileEditorProvider>
  abstract val isPreview: Boolean
}
