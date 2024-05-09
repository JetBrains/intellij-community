// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor

import com.intellij.openapi.project.Project
import java.awt.datatransfer.Transferable
import java.io.File

interface FileDropHandler {
  suspend fun handleDrop(project: Project,
                         t: Transferable,
                         files: Collection<File>,
                         editor: Editor?): Boolean
}