// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl

import com.intellij.openapi.vfs.FileIdAdapter
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.VirtualFileWithId

private class FileIdAdapterImpl : FileIdAdapter {

  override fun getFile(id: Int): VirtualFile? = VirtualFileManager.getInstance().findFileById(id)

  override fun getId(file: VirtualFile): Int? = (file as? VirtualFileWithId)?.id

}
