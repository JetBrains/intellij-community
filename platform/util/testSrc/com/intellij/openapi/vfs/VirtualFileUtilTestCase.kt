// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs

import com.intellij.openapi.util.io.NioPathUtilTestCase
import com.intellij.testFramework.utils.vfs.refreshAndGetVirtualDirectory
import java.nio.file.Path

abstract class VirtualFileUtilTestCase : NioPathUtilTestCase() {

  suspend fun FileAssertion<Path>.assertVirtualFile(action: suspend Path.() -> VirtualFile?): FileAssertion<VirtualFile> {
    val file = getFile()!!
    return this@VirtualFileUtilTestCase.assertVirtualFile { file.action() }
  }

  suspend fun assertVirtualFile(init: suspend VirtualFile.() -> VirtualFile?): FileAssertion<VirtualFile> {
    return VirtualFileAssertion().init { refreshAndGetVirtualDirectory().init() }
  }

  inner class VirtualFileAssertion : FileAssertion<VirtualFile>() {

    override suspend fun createAssertion(init: suspend VirtualFile.() -> VirtualFile?) =
      assertVirtualFile(init)

    override fun isEmpty(file: VirtualFile) = file.children.isEmpty()
    override fun exists(file: VirtualFile) = file.exists()
    override fun isFile(file: VirtualFile) = file.isFile
    override fun isDirectory(file: VirtualFile) = file.isDirectory
  }
}