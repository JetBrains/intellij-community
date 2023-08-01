// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs

import com.intellij.openapi.util.io.FileAssertion

class VirtualFileAssertion : FileAssertion<VirtualFile>() {

  override fun createAssertion() = VirtualFileAssertion()
  override fun isEmpty(file: VirtualFile) = file.children.isEmpty()
  override fun exists(file: VirtualFile) = file.exists()
  override fun isFile(file: VirtualFile) = file.isFile
  override fun isDirectory(file: VirtualFile) = file.isDirectory

  companion object {

    suspend fun assertVirtualFile(init: suspend () -> VirtualFile?): FileAssertion<VirtualFile> {
      return VirtualFileAssertion().init { init() }
    }
  }
}