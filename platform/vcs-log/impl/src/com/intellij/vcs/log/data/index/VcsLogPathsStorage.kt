// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data.index

import com.intellij.vcs.log.data.index.VcsLogPathsIndex.LightFilePath
import java.io.IOException

internal interface VcsLogPathsStorage {
  @Throws(IOException::class)
  fun getPath(pathId: Int): LightFilePath?

  @Throws(IOException::class)
  fun getPathId(filePath: LightFilePath): Int

  fun flush() {}

  @Throws(IOException::class)
  fun close() {}

}
