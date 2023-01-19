// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.fixtures

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.IdeaTestFixture
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.testFramework.configuration.TestFilesConfiguration

@ApiStatus.Experimental
interface FileTestFixture : IdeaTestFixture {

  val root: VirtualFile

  fun isModified(): Boolean

  fun hasErrors(): Boolean

  fun snapshot(relativePath: String)

  fun rollback(relativePath: String)

  fun rollbackAll()

  fun suppressErrors(isSuppressedErrors: Boolean)

  fun addIllegalOperationError(message: String)

  interface Builder : TestFilesConfiguration {

    fun excludeFiles(vararg relativePath: String)
  }
}