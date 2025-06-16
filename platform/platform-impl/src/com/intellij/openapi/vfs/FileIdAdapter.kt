// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs

import com.intellij.openapi.components.service
import com.intellij.util.application
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface FileIdAdapter {

  companion object {
    fun getInstance(): FileIdAdapter = application.service()
  }

  fun getFile(id: Int): VirtualFile?

  fun getId(file: VirtualFile): Int?

}
