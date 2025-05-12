// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.zombie

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithId

private class ZombieOriginRecipeBookImpl : ZombieOriginRecipeBook {
  override fun getIdForFile(file: VirtualFile): Int? = (file as? VirtualFileWithId)?.id
}