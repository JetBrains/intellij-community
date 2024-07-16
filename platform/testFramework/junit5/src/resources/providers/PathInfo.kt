// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.resources.providers

import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.removeUserData
import com.intellij.testFramework.junit5.resources.providers.PathInfo.Companion.addPathInfoToDeleteOnExit
import com.intellij.testFramework.junit5.resources.providers.PathInfo.Companion.cleanUpPathInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path

/**
 * Resources like module and project might have [path].
 * Such path may need to be [deletePathOnExit] after the test.
 * For any but local filesystem, we need to [closeFsOnExit].
 *
 * When resource created, call [UserDataHolder.addPathInfoToDeleteOnExit].
 * On deletion, call [cleanUpPathInfo]
 */
data class PathInfo(val path: Path, private val deletePathOnExit: Boolean = true, private val closeFsOnExit: Boolean = false) {
  private suspend fun close(): Unit = withContext(Dispatchers.IO) {
    val fileSystem = path.fileSystem
    if (deletePathOnExit) {
      Files.deleteIfExists(path)
    }
    if (closeFsOnExit) {
      fileSystem.close()
    }
  }

  companion object {
    private val key = Key<PathInfo>("pathInfoToDelete")
    internal fun UserDataHolder.addPathInfoToDeleteOnExit(info: PathInfo) {
      putUserData(key, info)
    }

    internal suspend fun UserDataHolder.cleanUpPathInfo() {
      getUserData(key)?.close()
      removeUserData(key)
    }
  }
}