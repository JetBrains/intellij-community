// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.testFramework.configuration

import com.intellij.openapi.vfs.VirtualFile

interface TestFilesConfiguration {

  fun withDirectory(relativePath: String)

  fun withFile(relativePath: String, content: String)

  fun withFiles(action: suspend (VirtualFile) -> Unit)

  fun areContentsEqual(root: VirtualFile): Boolean

  fun assertContentsAreEqual(root: VirtualFile)

  suspend fun createFiles(root: VirtualFile)
}