// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.IdeaTestFixture
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface FileTestFixture : IdeaTestFixture {

  fun isModified(): Boolean

  fun snapshot(relativePath: String)

  fun rollback(relativePath: String)

  fun suppressErrors(isSuppressedErrors: Boolean)

  fun addIllegalOperationError(message: String)

  interface Builder {

    fun withFile(relativePath: String, content: String)

    fun withFiles(action: (VirtualFile) -> Unit)
  }
}