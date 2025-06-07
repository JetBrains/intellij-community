// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.zombie

import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.application
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface ZombieOriginRecipeBook {
  companion object {
    fun getInstance(): ZombieOriginRecipeBook = application.service()
  }
  fun getIdForFile(file: VirtualFile): Int?
}