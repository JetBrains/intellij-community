// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.configuration

import com.intellij.openapi.vfs.VirtualFile

interface TestFilesConfiguration {

  fun findFile(relativePath: String): String?

  fun getFile(relativePath: String): String

  fun withFile(relativePath: String, content: String)

  fun withFiles(action: (VirtualFile) -> Unit)

  fun areContentsEqual(root: VirtualFile): Boolean

  fun createFiles(root: VirtualFile)
}