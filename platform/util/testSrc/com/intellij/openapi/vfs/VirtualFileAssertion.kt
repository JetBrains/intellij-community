// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs

import com.intellij.openapi.util.io.FileAssertion
import com.intellij.openapi.util.io.NioPathAssertion
import java.nio.file.Path

class VirtualFileAssertion : FileAssertion<VirtualFile, VirtualFileAssertion>() {

  override val self: VirtualFileAssertion get() = this

  override fun createAssertion() = VirtualFileAssertion()
  override fun isEmpty(file: VirtualFile) = file.children.isEmpty()
  override fun exists(file: VirtualFile) = file.exists()
  override fun isFile(file: VirtualFile) = file.isFile
  override fun isDirectory(file: VirtualFile) = file.isDirectory

  suspend fun isNioPathEqualsTo(path: Path) = apply {
    NioPathAssertion.assertNioPath { getFile()!!.toNioPath() }
      .isEqualsTo { path }
  }

  companion object {

    suspend fun assertVirtualFile(init: suspend () -> VirtualFile?): VirtualFileAssertion {
      return VirtualFileAssertion().init { init() }
    }
  }
}