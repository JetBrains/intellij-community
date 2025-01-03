// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.eel.impl

import com.intellij.platform.eel.EelPathMapper
import com.intellij.platform.eel.fs.EelFileSystemApi
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.path.EelPath.OS
import com.intellij.platform.testFramework.junit5.eel.impl.nio.EelUnitTestFileSystem
import com.intellij.platform.testFramework.junit5.eel.impl.nio.EelUnitTestPath
import kotlinx.coroutines.CoroutineScope
import java.nio.file.Path

internal class EelTestPathMapper(val os: OS, val fileSystem: EelUnitTestFileSystem, val localPrefix: String) : EelPathMapper {
  override fun getOriginalPath(path: Path): EelPath? {
    val relativeRemainder = if (path.toString().startsWith(localPrefix) || path is EelUnitTestPath) {
      path.map { it.toString() }
    }
    else {
      return null
    }

    val root = if (os == OS.WINDOWS) {
      EelPath.parse(FAKE_WINDOWS_ROOT, os)
    }
    else {
      EelPath.parse("/", os)
    }
    return relativeRemainder.fold(root, EelPath::resolve)
  }

  override suspend fun maybeUploadPath(path: Path, scope: CoroutineScope, options: EelFileSystemApi.CreateTemporaryEntryOptions): EelPath {
    TODO("Not yet implemented")
  }

  override fun toNioPath(path: EelPath): Path {
    return path.parts.fold(Path.of(localPrefix), Path::resolve)
  }

  override fun pathPrefix(): String {
    TODO()
  }
}